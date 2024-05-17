package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopServiceImpl;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Test
    public void test_01() throws InterruptedException {
        shopServiceImpl.saveShop2Redis(1L, 20L);
    }

    @Test
    public void test_02(){
        long l = redisIdWorker.nextId("vocher");
    }

}
