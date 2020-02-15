package com.myseckill.dao;

import com.myseckill.entity.Seckill;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({"classpath:spring/spring-dao.xml"})
public class SeckillDaoTest {

    @Resource
    private SeckillDao seckillDao;

    @Test
    public void reduceNumber() {
        long seckillId = 1000L;
        Date date = new Date();
        int updteCount = seckillDao.reduceNumber(seckillId, date);
        System.out.println(updteCount);
    }

    @Test
    public void queryById() {
        long seckillId = 1000L;
        Seckill seckill = seckillDao.queryById(seckillId);
        System.out.println(seckill);
    }

    @Test
    public void queryAll() {
        List<Seckill> seckills = new ArrayList<>();
        seckills = seckillDao.queryAll(0, 10);
        for (Seckill seckill:
             seckills) {
            System.out.println(seckill);
        }
    }
}