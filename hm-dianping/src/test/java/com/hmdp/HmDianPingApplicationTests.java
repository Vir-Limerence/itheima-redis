package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopServiceImpl;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private UserServiceImpl userServiceImpl;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void test_01() throws InterruptedException {
        shopServiceImpl.saveShop2Redis(1L, 20L);
    }

    @Test
    public void test_02(){
        long l = redisIdWorker.nextId("vocher");
    }

    @Test
    public void test_o3(){
        //1.获取数据库中的所有用户信息
        List<User> userList = userServiceImpl.query().list();
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

}
