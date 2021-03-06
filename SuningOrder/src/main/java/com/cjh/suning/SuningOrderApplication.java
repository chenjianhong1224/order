package com.cjh.suning;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.cjh.suning.bean.spring.SpringContextUtils;
import com.cjh.suning.config.ApplicationConfig;
import com.cjh.suning.task.I360OrderTask;
import com.cjh.suning.task.OrderTask;
import com.google.common.collect.Lists;

@SpringBootApplication
public class SuningOrderApplication {

	public static void main(String[] args) throws ParseException {
		SpringApplication.run(SuningOrderApplication.class, args);
		ApplicationConfig applicationConfig = (ApplicationConfig) SpringContextUtils.getContext()
				.getBean("applicationConfig");
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date beginTime = formatter.parse(applicationConfig.getTaskBeginTime());
		Date endTime = formatter.parse(applicationConfig.getTaskEndTime());
		int threadNum = Integer.valueOf(applicationConfig.getTaskNum());
		threadNum = 1;
		List<Thread> threadList = Lists.newArrayList();
		try {
			if (applicationConfig.getTaskPlatform().equals("1")) {
				if (applicationConfig.getCloseDefaultTask().equals("0")) {
					OrderTask delay1sTask = new OrderTask(new Date(beginTime.getTime() + 1000), endTime);
					Thread delay1sthread = new Thread(delay1sTask, "work-delay1s");
					threadList.add(delay1sthread);
					delay1sthread.start();
					OrderTask rush1sTask = new OrderTask(new Date(beginTime.getTime() - 1000), endTime);
					Thread rush1sthread = new Thread(rush1sTask, "work-rush1s");
					threadList.add(rush1sthread);
					rush1sthread.start();
				}
				for (int i = 0; i < threadNum; i++) {
					OrderTask task = new OrderTask(beginTime, endTime);
					Thread thread = new Thread(task, "work-" + i);
					threadList.add(thread);
					thread.start();
					Thread.sleep(600);
				}
			} else {
				I360OrderTask task = new I360OrderTask(beginTime, endTime);
				Thread thread = new Thread(task, "work-0");
				threadList.add(thread);
				thread.start();
			}
			while (OrderTask.getLoginStatus().get() == 0) {
				Thread.sleep(10);
			}
			if (OrderTask.getLoginStatus().get() == 2) {
				if (applicationConfig.getBackFlag().equals("0")) {
					Scanner input = new Scanner(System.in);
					String val = null;
					do {
						val = input.nextLine();
						Thread.sleep(10);
					} while (!val.equals("q"));
					System.out.println("你输入了\"q\", 程序正在退出，请勿关闭！");
					input.close();
				} else {
					Date now = new Date();
					while ((now.getTime() < endTime.getTime()) && !OrderTask.getTaskFinishFlag().get()) {
						now = new Date();
						Thread.sleep(10);
					}
				}
			}
			if (applicationConfig.getTaskPlatform().equals("1")) {
				OrderTask.setTaskFinish();
			} else {
				I360OrderTask.setTaskFinish();
			}
			for (Thread thread : threadList) {
				thread.join();
			}
			System.out.println("程序正常退出");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
