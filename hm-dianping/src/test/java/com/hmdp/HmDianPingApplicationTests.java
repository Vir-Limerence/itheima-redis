package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private IShopService shopService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;



    @Test
    public void test_01() throws InterruptedException {
//        shopService.saveShop2Redis(1L, 20L);
    }

    @Test
    public void test_02(){
        long l = redisIdWorker.nextId("vocher");
    }

    @Test
    public void test_03(){
        //1.获取数据库中的所有用户信息
        List<User> userList = userService.query().list();
        //2.批量注册用户到Redis
        List<String> tokenList = new ArrayList<>();
        for (User user : userList) {
            //5.将用户信息保存到redis
            //5.1 生成随机token
            String token = UUID.randomUUID().toString(true);
            tokenList.add(token);
            //5.2利用hutool创建userDTO
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            //5.3将userDTO转换成MAP
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create().setIgnoreNullValue(true)
                            .setFieldValueEditor((filedName, filedValue) -> filedValue==null?null:filedValue.toString()));
            String key = RedisConstants.LOGIN_USER_KEY+token;
            stringRedisTemplate.opsForHash().putAll(key, userMap);
            stringRedisTemplate.expire(key, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }
        //3.保存tokenList
        try (PrintWriter writer = new PrintWriter("D:\\out.txt")) {
            for (String token : tokenList) {
                writer.println(token);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void test_04(){
        //1. 查询所有店铺信息
        List<Shop> list = shopService.list();
        //2. 把店铺信息分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.写入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1 获取类型id
            Long typeId = entry.getKey();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            //3.2 获取同类型的店铺集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            //3.3 写入redis GEOADD key 经度 维度 member
            for (Shop shop : value) {
//                stringRedisTemplate.opsForGeo().add(
//                        key, new Point(shop.getX(), shop.getY()),shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Test
    public void test_05(){
        // 准备数组 装用户数据
        String[] users = new String[1000];
        int index = 0;
        for(int i=1;i<=1000000;i++){
            //赋值
            users[index++] = "user_" + i;
            // 每1000条发送一次
            if(i%1000 == 0){
                index = 0;
                stringRedisTemplate.opsForHyperLogLog().add("hll1", users);
            }
        }
        //统计数量
        Long size = stringRedisTemplate.opsForHyperLogLog().size("hll1");
        System.out.println("size=" + size);
    }

}
