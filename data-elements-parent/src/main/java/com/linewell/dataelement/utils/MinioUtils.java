package com.linewell.dataelement.utils;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@Slf4j
public class MinioUtils {

    private static final Logger logger = LoggerFactory.getLogger(MinioUtils.class);

    private static MinioClient minioClient;

    // 初始化MinIO客户端
    public static MinioClient getMinioClient(String ENDPOINT, String ACCESS_KEY, String SECRET_KEY) {
        minioClient = MinioClient.builder()
                .endpoint(ENDPOINT)
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build();
        return minioClient;
    }

    /**
     * 检查桶是否存在
     * @param bucketName 桶名称
     * @return 是否存在
     */
    public static boolean bucketExists(String bucketName) throws Exception {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    /**
     * 确保桶存在，不存在则创建
     */
    private static void ensureBucketExists(MinioClient client, String bucketName) throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            logger.info("创建目标桶: " + bucketName);
        }
    }

    /**
     * 检查对象是否存在
     */
    public static boolean isObjectExists(String bucketName, String objectName) throws Exception {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return false;
            }
            throw e;
        }
    }

    /**
     * 创建桶
     * @param bucketName 桶名称
     */
    public static void createBucket(String bucketName) throws Exception {
        if (!bucketExists(bucketName)) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    /**
     * 上传文件
     * @param bucketName 桶名称
     * @param objectName 存储在MinIO中的文件名
     * @param file 要上传的文件
     */
    public static String uploadFile(String bucketName, String objectName, MultipartFile file) throws Exception {
        createBucket(bucketName); // 确保桶存在
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );
        return "操作成功";
    }

    /**
     * 下载文件
     * @param bucketName 桶名称
     * @param objectName 存储在MinIO中的文件名
     * @param outputStream 输出流，用于接收文件内容
     */
    public static void downloadFile(String bucketName, String objectName, OutputStream outputStream) throws Exception {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build())) {

            // 将输入流复制到输出流
            byte[] buffer = new byte[1024];
            int length;
            while ((length = stream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
        }
    }

    /**
     * 删除文件
     * @param bucketName 桶名称
     * @param objectName 存储在MinIO中的文件名
     */
    public static String deleteFile(String bucketName, String objectName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );
        return "操作成功";
    }

    /**
     * 列出桶中所有文件
     * @param bucketName 桶名称
     * @return 文件名称列表
     */
    public static List<String> listFiles(String bucketName) throws Exception {
        List<String> objectNames = new ArrayList<>();
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .recursive(true)
                        .build()
        );
        for (Result<Item> result : results) {
            Item item = result.get();
            objectNames.add(item.objectName());
        }
        return objectNames;
    }

    /**
     * 获取文件的临时访问URL
     * @param bucketName 桶名称
     * @param objectName 存储在MinIO中的文件名
     * @param expiry 过期时间（单位：秒）
     * @return 临时访问URL
     */
    public static String getPresignedObjectUrl(String bucketName, String objectName, int expiry) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketName)
                        .object(objectName)
                        .expiry(expiry, TimeUnit.SECONDS)
                        .build()
        );
    }

    /**
     * 同步源桶到目标桶
     * @param sourceBucket 源桶名称
     * @param targetBucket 目标桶名称
     * @param recursive 是否递归同步子目录
     * @throws Exception 可能抛出的异常
     */
    public static void syncBuckets(String sourceBucket, String targetBucket, boolean recursive) throws Exception {
        // 确保目标桶存在
        ensureBucketExists(minioClient, targetBucket);
        // 列出源桶中的所有对象
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(sourceBucket)
                        .recursive(recursive)
                        .build()
        );
        Iterator<Result<Item>> iterator = results.iterator();
        int successCount = 0;
        int failCount = 0;
        logger.info("开始同步 " + sourceBucket + " 到 " + targetBucket + "...");
        while (iterator.hasNext()) {
            try {
                Item item = iterator.next().get();
                // 跳过目录（只处理文件）
                if (item.isDir()) {
                    continue;
                }
                String objectName = item.objectName();
                logger.info("同步文件: " + objectName);
                // 复制对象
                copyObject(minioClient, sourceBucket, targetBucket, objectName);
                successCount++;
            } catch (Exception e) {
                logger.error("同步失败: " + e.getMessage());
                failCount++;
            }
        }
        logger.info("同步完成 - 成功: " + successCount + ", 失败: " + failCount);
    }

    /**
     * 复制单个对象
     */
    private static void copyObject(MinioClient client, String sourceBucket,
                                   String targetBucket, String objectName) throws Exception {
        client.copyObject(
                CopyObjectArgs.builder()
                        .source(
                                CopySource.builder()
                                        .bucket(sourceBucket)
                                        .object(objectName)
                                        .build()
                        )
                        .bucket(targetBucket)
                        .object(objectName)
                        .build()
        );
    }

    /**
     * 同步指定文件到目标桶
     * @param sourceBucket 源桶名称
     * @param targetBucket 目标桶名称
     * @param fileNames 需要同步的文件名列表
     * @throws Exception 可能抛出的异常
     */
    public static String syncSpecificFiles(String sourceBucket, String targetBucket, List<String> fileNames) throws Exception {
        // 确保目标桶存在
        ensureBucketExists(minioClient, targetBucket);
        int successCount = 0;
        int failCount = 0;
        for (String fileName : fileNames) {
            try {
                // 检查源文件是否存在
                if (!isObjectExists(sourceBucket, fileName)) {
                    logger.error("文件不存在: " + fileName);
                    failCount++;
                    continue;
                }
                // 复制文件
                copyObject(minioClient, sourceBucket, targetBucket, fileName);
                successCount++;
            } catch (Exception e) {
                logger.error("同步文件 " + fileName + " 失败: " + e.getMessage());
                failCount++;
            }
        }
        return "发送成功: " + successCount + "个文件, 失败: " + failCount + "个文件";
    }
}
