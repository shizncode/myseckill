package com.myseckill.web;

import com.myseckill.dto.Exposer;
import com.myseckill.dto.SeckillExecution;
import com.myseckill.dto.SeckillResult;
import com.myseckill.entity.Seckill;
import com.myseckill.enums.SeckillStateEnum;
import com.myseckill.exception.RepeatKillException;
import com.myseckill.exception.SeckillCloseException;
import com.myseckill.service.SeckillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@Controller
@RequestMapping("/seckill")
public class SeckillControllor {

    private static final Logger logger = LoggerFactory.getLogger(SeckillControllor.class);

    @Autowired
    private SeckillService seckillService;

    //显示秒杀商品信息列表
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public String list(Model model) {
        //list.jsp+mode=ModelAndView
        //获取列表页
        List<Seckill> list = seckillService.getSeckillList();
        model.addAttribute("list", list);
        return "list"; //"/WEB-INF/jsp/list.sjp"
    }

    //显示秒杀商品详情
    @RequestMapping(value = "/{seckillId}/detail", method = RequestMethod.GET)
    public String detail(@PathVariable("seckillId") Long seckillId, Model model) {
        if (seckillId == null) {
            return "redirect:/seckill/list";   //请求重定向
        }

        Seckill seckill = seckillService.getById(seckillId);
        if (seckill == null) {
            return "forward:/seckill/list";   //请求转发
        }
        model.addAttribute("seckill", seckill);
        return "detail";
    }

    //ajax ,json暴露秒杀接口的方法
    @RequestMapping(value = "/{seckillId}/exposer",
            method = RequestMethod.GET,
            produces = {"application/json;charset=UTF-8"})
    @ResponseBody
    public SeckillResult<Exposer> exposer(@PathVariable("seckillId") Long seckillId) {
        SeckillResult<Exposer> result;
        try {
//            Exposer exposer = seckillService.exportSeckillUrl(seckillId);
            Exposer exposer = seckillService.exportSeckillUrlRedis(seckillId);
            result = new SeckillResult<Exposer>(true, exposer);
        } catch (Exception e) {
            e.printStackTrace();
            result = new SeckillResult<Exposer>(false, e.getMessage());
        }

        return result;
    }


    //执行秒杀过程
    @RequestMapping(value = "/{seckillId}/{md5}/execution",
            method = RequestMethod.POST,
            produces = {"application/json;charset=UTF-8"})
    @ResponseBody
    public SeckillResult<SeckillExecution> execute(@PathVariable("seckillId") Long seckillId,
                                                   @PathVariable("md5") String md5,
                                                   @CookieValue(value = "userPhone", required = false) Long userPhone) {
        if (userPhone == null) {
            return new SeckillResult<SeckillExecution>(false, "未注册");

        }
        SeckillExecution seckillExecution = seckillService.executeSeckillProcedure(seckillId, userPhone, md5);
        return new SeckillResult<SeckillExecution>(true, seckillExecution);
//        try {
//            //存储过程调用
//            SeckillExecution seckillExecution = seckillService.executeSeckill(seckillId, userPhone, md5);
//            return new SeckillResult<SeckillExecution>(true, seckillExecution);
//        } catch (RepeatKillException e1) {
//            SeckillExecution execution = new SeckillExecution(seckillId, SeckillStateEnum.REPEAT_KILL);
//            return new SeckillResult<SeckillExecution>(true, execution);
//        } catch (SeckillCloseException e2) {
//            SeckillExecution execution = new SeckillExecution(seckillId, SeckillStateEnum.END);
//            return new SeckillResult<SeckillExecution>(true, execution);
//        } catch (Exception e) {
//            SeckillExecution execution = new SeckillExecution(seckillId, SeckillStateEnum.INNER_ERROR);
//            return new SeckillResult<SeckillExecution>(true, execution);
//        }
    }

    //获取系统时间
    @RequestMapping(value = "/time/now", method = RequestMethod.GET, produces = {"application/json;charset=UTF-8"})
    @ResponseBody
    public SeckillResult<Long> time() {
        Date now = new Date();
        return new SeckillResult<Long>(true, now.getTime());
    }
}
