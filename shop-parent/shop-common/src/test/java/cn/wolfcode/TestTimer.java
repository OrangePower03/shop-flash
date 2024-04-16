package cn.wolfcode;

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;

public class TestTimer {
    @Test
    public void test() throws InterruptedException {
        TimerTask timerTask=new TimerTask() {
            @Override
            public void run() {
                System.out.println("启动" + new Date());
            }
        };

//        Timer timer = new Timer();
//        timer.schedule(timerTask, 2000, 2000);
         ScheduledThreadPoolExecutor executor=new ScheduledThreadPoolExecutor(
                6,
                Executors.defaultThreadFactory()
        );
        executor.scheduleAtFixedRate(timerTask, 0, 2,TimeUnit.SECONDS);
        TimeUnit.SECONDS.sleep(99999);
    }

    @Test
    public void t() {

    }
}
