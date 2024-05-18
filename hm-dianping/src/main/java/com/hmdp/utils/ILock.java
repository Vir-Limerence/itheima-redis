package com.hmdp.utils;

public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的超时时间，过时自动释放
     * @return boolean
     */
    public boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    public void unlock();
}
