package com.alekseyorlov.vkdump.client.executor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.alekseyorlov.vkdump.client.executor.query.QueryWrapper;
import com.alekseyorlov.vkdump.client.executor.query.QueryResult;
import com.alekseyorlov.vkdump.client.executor.query.QueryWrapperExecutor;
import com.alekseyorlov.vkdump.client.executor.query.message.QueryResultMessage;
import com.vk.api.sdk.client.ApiRequest;

public class VKApiRequestExecutor {
    
    private DelayQueue<QueryWrapper<?>> queue = new DelayQueue<>();

    private Long batchWindowLength;
    
    private TimeUnit batchWindowLengthTimeUnit;
    
    private ExecutorService writerExecutor;
    
    private  ScheduledExecutorService readerExecutor = Executors.newScheduledThreadPool(1);
    
    public VKApiRequestExecutor(
            Integer maxRequestsCount,
            Long batchWindowLength,
            TimeUnit batchWindowLengthTimeUnit) {
        this.batchWindowLength = batchWindowLength;
        this.batchWindowLengthTimeUnit = batchWindowLengthTimeUnit;
        
        writerExecutor = Executors.newFixedThreadPool(maxRequestsCount);
        
        readerExecutor.scheduleWithFixedDelay(new Runnable() {

            @Override
            public void run() {
                for(int i = 0; i < maxRequestsCount; i++) {
                    QueryWrapper<?> queryWrapper = queue.poll();
                    if (queryWrapper != null) {
                        writerExecutor.submit(new QueryWrapperExecutor(queryWrapper));
                    }
                }
            }
        }, 0L, batchWindowLength, batchWindowLengthTimeUnit);
    }
 
    public <Query extends ApiRequest<Result>, Result> Future<Result> execute(Query query) throws InterruptedException {

        BlockingQueue<QueryResultMessage<Result>> channel = new ArrayBlockingQueue<>(1);
        queue.put(new QueryWrapper<Result>(query, channel, batchWindowLength, batchWindowLengthTimeUnit));
        
        return new QueryResult<Result>(channel);
    }
}
