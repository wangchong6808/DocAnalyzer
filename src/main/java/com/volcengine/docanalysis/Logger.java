package com.volcengine.docanalysis;

import org.apache.commons.lang.time.DateFormatUtils;

import java.util.Date;

public class Logger {

    public static void log (String content) {
        String datePrefix = DateFormatUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss");
        String callerClass = Thread.currentThread()
                                .getStackTrace()[2]
                                .getClassName();
        System.out.printf("%s %s -- %s%n", datePrefix, callerClass, content);
    }

    public static String getExecutionTime(Date startTime) {
        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime.getTime();
        long totalSeconds = durationMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d分%d秒", minutes, seconds);
    }
}
