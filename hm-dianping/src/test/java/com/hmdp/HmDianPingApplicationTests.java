package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopServiceImpl;

    @Test
    public void test_01() throws InterruptedException {
        shopServiceImpl.saveShop2Redis(1L, 20L);
    }

}
