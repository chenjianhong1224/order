package com.cjh.suning.task;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cjh.suning.bean.ReturnResultBean;
import com.cjh.suning.bean.spring.SpringContextUtils;
import com.cjh.suning.config.ApplicationConfig;
import com.cjh.suning.service.SuningService;

public class OrderTask implements Runnable {

	public OrderTask(Date beginTime, Date endTime) {
		this.beginTime = beginTime;
		this.endTime = endTime;
	}

	Date beginTime;

	Date endTime;

	private static AtomicBoolean taskFinishFlag = new AtomicBoolean(false);;

	private Logger log = LoggerFactory.getLogger(OrderTask.class);

	@Override
	public void run() {
		SuningService suningService = null;
		try {
			ApplicationConfig applicationConfig = (ApplicationConfig) SpringContextUtils.getContext()
					.getBean("applicationConfig");
			Date now = new Date();
			if (applicationConfig.getModel().equals("1")) {
				suningService = (SuningService) SpringContextUtils.getContext().getBean("SuningServiceChromeDriver");
			}
			ReturnResultBean result = suningService.login(applicationConfig.getUserName(),
					applicationConfig.getPassword());
			if (result.getResultCode() == 0) {
				boolean cached = false;
				suningService.refresh(applicationConfig.getSkuUrl());
				now = new Date();
				if (now.getTime() < beginTime.getTime()) {
					log.info("还未到点, 休息中...");
				}
				if (now.getTime() > endTime.getTime()) {
					log.info("到点了, 收工...");
					return;
				}
				while (now.getTime() < beginTime.getTime()) {
					if ((beginTime.getTime() - now.getTime()) < (2 * 1000 * 60)) { // 小于两分钟
						Thread.sleep(1);
						if (!cached) {
							suningService.testOrder(applicationConfig.getSkuUrl(), applicationConfig.getSkuColor(),
									applicationConfig.getSkuVersion(), applicationConfig.getSkuPhonedl());
							cached = true;
						}
						now = new Date();
						continue; // 提高刷新频率
					}
					Thread.sleep(1000 * 60 * 1); // 1分钟刷新一次，避免session失效
					suningService.refresh(applicationConfig.getSkuUrl());
					now = new Date();
				}
				log.info("到点了, 开始干活...");
				long count = 0;
				long sleepTime = 1;
				float sumExcTime = 0;
				while (!taskFinishFlag.get()) {
					now = new Date();
					if (now.getTime() > endTime.getTime()) {
						log.info("到点了, 收工...");
						return;
					}
					long startTime = System.currentTimeMillis();
					result = suningService.order(applicationConfig.getSkuUrl(), applicationConfig.getSkuColor(),
							applicationConfig.getSkuVersion(), applicationConfig.getSkuPhonedl(),
							applicationConfig.getSkuBuyNum(), applicationConfig.getSkuCheckPayAmount());
					float excTime = (float) (System.currentTimeMillis() - startTime) / 1000;
					log.info("本次作业花费时间：" + excTime + "秒");
					if (result.getResultCode() == 0) {
						log.info("下单成功, 收队咯");
						taskFinishFlag.set(true);
						break;
					}
					log.info(result.getReturnMsg());
					if (result.getResultCode() == -2) {
						log.info("程序将在300秒后再轮询下单，期间按q退出无用, o(╥﹏╥)o");
						Thread.sleep(300 * 1000);
					}
					if (now.getTime() > endTime.getTime()) {
						log.info("到点了, 打卡下班");
						break;
					}
					count++;
					sumExcTime += excTime;
					if (count == 5) {
						float avgExcTime = sumExcTime / count;
						float leftTime = 60f - sumExcTime;
						if (leftTime > 0) {
							if (avgExcTime > 5.8) {
								sleepTime = (long) Math.ceil((leftTime - avgExcTime * 2) * 1000 / 5);
							} else {
								sleepTime = (long) Math.ceil((leftTime - avgExcTime) * 1000 / 5);
							}
						} else {
							sleepTime = 1;
						}
						log.info("为了避免苏宁风控, 调整轮询时间间隔为" + sleepTime / 1000 + "秒");
					}
					if (count == 10) {
						log.info("恢复轮询时间间隔");
						count = 0;
						sleepTime = 1;
						sumExcTime = 0;
					}
					Thread.sleep(sleepTime);
				}
			}
			log.info(result.getReturnMsg());
		} catch (InterruptedException e1) {
			log.info("任务中断...");
		} catch (Exception e2) {
			log.info("任务异常", e2);
		} finally {
			log.info("任务退出");
			if (suningService != null) {
				suningService.destroy();
			}
		}
	}

	public static AtomicBoolean getTaskFinishFlag() {
		return taskFinishFlag;
	}

	public static void setTaskFinish() {
		taskFinishFlag.set(true);
	}

}
