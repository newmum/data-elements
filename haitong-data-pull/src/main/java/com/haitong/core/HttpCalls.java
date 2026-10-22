package com.haitong.core;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Bounded response and whole-exchange deadline, including a slow streaming response body. */
public final class HttpCalls {
    private HttpCalls() {}
    public record Result(int status,byte[] body) {}
    public static Result call(URI fixedUrl,String method,byte[] body,Map<String,String> headers,int seconds,int maxResponseBytes) throws Exception {
        HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.min(seconds,10)))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        CompletableFuture<HttpResponse<byte[]>> future=null;
        try {
            var builder=HttpRequest.newBuilder(fixedUrl).timeout(Duration.ofSeconds(seconds));headers.forEach(builder::header);
            builder.method(method,HttpRequest.BodyPublishers.ofByteArray(body));
            future=client.sendAsync(builder.build(),info->new LimitedBody(maxResponseBytes));
            HttpResponse<byte[]> response=future.get(seconds,TimeUnit.SECONDS);
            return new Result(response.statusCode(),response.body());
        } finally {
            if(future!=null && !future.isDone()) future.cancel(true);
            client.shutdownNow();
        }
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int max;private final ByteArrayOutputStream out=new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();private Flow.Subscription subscription;
        LimitedBody(int max) {this.max=max;}
        @Override public CompletionStage<byte[]> getBody() {return result;}
        @Override public void onSubscribe(Flow.Subscription s) {this.subscription=s;s.request(1);}
        @Override public void onNext(List<ByteBuffer> buffers) {
            try {
                for(ByteBuffer b:buffers) {
                    if((long)out.size()+b.remaining()>max) throw new IllegalArgumentException("HTTP_RESPONSE_TOO_LARGE");
                    byte[] chunk=new byte[b.remaining()];b.get(chunk);out.writeBytes(chunk);
                }
                subscription.request(1);
            } catch(RuntimeException e) {subscription.cancel();result.completeExceptionally(e);}
        }
        @Override public void onError(Throwable error) {result.completeExceptionally(error);}
        @Override public void onComplete() {result.complete(out.toByteArray());}
    }
}
