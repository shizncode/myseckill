package com.myseckill.service.Impl;

import com.myseckill.dao.SeckillDao;
import com.myseckill.dao.SuccessKilledDao;
import com.myseckill.dao.cache.RedisDao;
import com.myseckill.dto.Exposer;
import com.myseckill.dto.SeckillExecution;
import com.myseckill.entity.Seckill;
import com.myseckill.entity.SuccessKilled;
import com.myseckill.enums.SeckillStateEnum;
import com.myseckill.exception.RepeatKillException;
import com.myseckill.exception.SeckillCloseException;
import com.myseckill.exception.SeckillException;
import com.myseckill.service.SeckillService;
import org.apache.commons.collections.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SeckillServiceImpl implements SeckillService {

    private static final Logger logger = LoggerFactory.getLogger(SeckillServiceImpl.class);

    // 加入一个混淆字符串(秒杀接口)的salt，为了避免用户猜出我们的md5值，值任意给，越复杂越好
    private final String salt = "akseh295sdssd52536";

    @Autowired
    private SeckillDao seckillDao;

    @Autowired
    private SuccessKilledDao successKilledDao;

    @Autowired
    private RedisDao redisDao;

    @Override
    public List<Seckill> getSeckillList() {
        return seckillDao.queryAll(0, 10);
    }

    @Override
    public Seckill getById(long seckillId) {
        return seckillDao.queryById(seckillId);
    }

    private String getMD5(long seckillId) {
        String base = seckillId + "/" + salt;
        //spring的工具类
        String md5 = DigestUtils.md5DigestAsHex(base.getBytes());
        return md5;
    }

    @Override
    public Exposer exportSeckillUrl(long seckillId) {
        // 秒杀商品不存在
        Seckill seckill = seckillDao.queryById(seckillId);
        if (seckill == null) {
            return new Exposer(false, seckillId);
        }
        // 不在秒杀时间范围内
        Date startTime = seckill.getStartTime();
        Date endTime = seckill.getEndTime();
        Date nowTime = new Date();
        if (nowTime.getTime() < startTime.getTime()
                || nowTime.getTime() > endTime.getTime()) {
            return new Exposer(false, seckillId, nowTime.getTime(), startTime.getTime(), endTime.getTime());
        }
        // 暴露秒杀地址
        String md5 = getMD5(seckillId);
        return new Exposer(true, md5, seckillId);
    }

    @Override
    public Exposer exportSeckillUrlRedis(long seckillId) {
        //优化点：缓存点 超时的基础上维护一致性  减低了数据库访问量
        // 1.访问redi
        Seckill seckill = redisDao.getSeckill(seckillId);
        if (seckill == null) {
            // 2.访问数据库
            seckill = seckillDao.queryById(seckillId);
            if (seckill == null) {// 说明查不到这个秒杀产品的记录
                return new Exposer(false, seckillId);
            } else {
                // 3.放入redis
                redisDao.putSeckill(seckill);
            }
        }
        /*
        Seckill seckill = seckillDao.queryById(seckillId);
        if (seckill == null) {
            return new Exposer(false, seckillId);
        }*/
        // 若是秒杀未开启
        Date startTime = seckill.getStartTime();
        Date endTime = seckill.getEndTime();
        // 系统当前时间
        Date nowTime = new Date();
        if (startTime.getTime() > nowTime.getTime() || endTime.getTime() < nowTime.getTime()) {
            return new Exposer(false, seckillId, nowTime.getTime(), startTime.getTime(), endTime.getTime());
        }
        // 秒杀开启，返回秒杀商品的id、用给接口加密的md5
        //转化特定字符的过程，不可逆
        String md5 = getMD5(seckillId);
        return new Exposer(true, md5, seckillId);
    }

    @Override
    @Transactional
    // 秒杀是否成功，成功:减库存，增加明细；失败:抛出异常，事务回滚
    /**
     * 使用注解控制事务方法的优点:
     * 1.开发团队达成一致约定，明确标注事务方法的编程风格
     * 2.保证事务方法的执行时间尽可能短，不要穿插其他网络操作RPC/HTTP请求或者剥离到事务方法外部
     * 3.不是所有的方法都需要事务，如只有一条修改操作、只读操作不要事务控制
     */
    public SeckillExecution executeSeckill(long seckillId, long userPhone, String md5)
            throws SeckillException, RepeatKillException, SeckillCloseException {
        if (md5 == null || !md5.equals(getMD5(seckillId))) {
            throw new SeckillException(SeckillStateEnum.DATA_REWRITE.getInfo());
        }
        /**
         * 将 减库存 插入购买明细  提交
         * 改为 插入购买明细 减库存 提交
         * 降低了网络延迟和GC影响，同时减少了rowLock的时间
         */
        Date nowTime = new Date();
        try {
            int insertCount = successKilledDao.insertSuccessKilled(seckillId, userPhone);
            if (insertCount <= 0) { // 重复秒杀
                throw new RepeatKillException(SeckillStateEnum.REPEAT_KILL.getInfo());
            } else { // 插入购买明细成功则执行减库存
                int updateCount = seckillDao.reduceNumber(seckillId, nowTime);
                if (updateCount <= 0) { // 秒杀结束
                    throw new SeckillCloseException(SeckillStateEnum.END.getInfo());
                } else { // 秒杀成功
                    SuccessKilled successKilled = successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
                    return new SeckillExecution(seckillId, SeckillStateEnum.SUCCESS, successKilled);
                }
            }
        } catch (RepeatKillException e1) {
            throw e1;
        } catch (SeckillCloseException e2) {
            throw e2;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            // 将编译期异常转化为运行期异常
            throw new SeckillException("seckill inner error:" + e.getMessage());
        }
    }

    @Override
    public SeckillExecution executeSeckillProcedure(long seckillId, long userPhone, String md5) {
        if (md5 == null || !md5.equals(getMD5(seckillId))) {
            return new SeckillExecution(seckillId, SeckillStateEnum.DATA_REWRITE);
        }
        Date killTime = new Date();
        Map<String, Object> map = new HashMap<>();
        map.put("seckillId", seckillId);
        map.put("phone", userPhone);
        map.put("killTime", killTime);
        map.put("result", null);
        // 执行储存过程,result被复制
        try {
            seckillDao.killByProcedure(map);
            // 获取result
            int result = MapUtils.getInteger(map, "result", -2);
            if (result == 1) {
                SuccessKilled successKilled = successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
                return new SeckillExecution(seckillId, SeckillStateEnum.SUCCESS, successKilled);
            } else {
                return new SeckillExecution(seckillId, SeckillStateEnum.stateOf(result));
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new SeckillExecution(seckillId, SeckillStateEnum.INNER_ERROR);
        }
    }

    public static void main(String[] args) {
        Date nowTime = new Date();
        System.out.println(nowTime);
        System.out.println(nowTime.getTime());
    }
}
