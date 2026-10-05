package com.linewell.dataelement.utils;


import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.linewell.dataelement.model.common.BaseBusinessException;

/**
 * http://blog.csdn.net/wangqiuyun/article/details/42143957
 * <p>
 * 前端的配合： https://github.com/rzcoder/node-rsa/issues/92，用es6实现rsa，仅支持chrome,
 * FireFox or Internet Explorer 11
 * https://github.com/travist/jsencrypt，用js实现rsa，支持ie6及以上
 */
public class RSAUtil {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * 随机生成密钥对
     *
     * @param filePath 密钥对要存储的路径
     */
    public static void genKeyPair(String filePath) {
        // KeyPairGenerator类用于生成公钥和私钥对，基于RSA算法生成对象
        KeyPairGenerator keyPairGen = null;
        try {
            keyPairGen = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // 初始化密钥对生成器，密钥大小为96-1024位
        keyPairGen.initialize(1024, new SecureRandom());
        // 生成一个密钥对，保存在keyPair中
        KeyPair keyPair = keyPairGen.generateKeyPair();
        // 得到私钥
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        // 得到公钥
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        try {
            // 得到公钥字符串
            String publicKeyString = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            // 得到私钥字符串
            String privateKeyString = Base64.getEncoder().encodeToString(privateKey.getEncoded());
            // 将密钥对写入到文件
            FileWriter pubfw = new FileWriter(filePath + "/publicKey.keystore");
            FileWriter prifw = new FileWriter(filePath + "/privateKey.keystore");
            BufferedWriter pubbw = new BufferedWriter(pubfw);
            BufferedWriter pribw = new BufferedWriter(prifw);
            pubbw.write(publicKeyString);
            pribw.write(privateKeyString);
            pubbw.flush();
            pubbw.close();
            pubfw.close();
            pribw.flush();
            pribw.close();
            prifw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 去除密钥的多余信息
     *
     * @param key 公钥或私钥字符串
     * @return Base64的密钥
     */
    public static String getBase64Key(String key) {
        key = key.replaceAll("-----BEGIN (.*)-----", "");
        key = key.replaceAll("-----END (.*)----", "");
        key = key.replaceAll("\r\n", "");
        key = key.replaceAll("\n", "");
        return key;
    }

    /**
     * 从字符串中加载公钥
     *
     * @param publicKeyStr 公钥数据字符串
     * @return 公钥
     */
    public static RSAPublicKey loadPublicKeyByStr(String publicKeyStr) {
        try {
            byte[] buffer = Base64.getDecoder().decode(publicKeyStr);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA", "BC"); // KeyFactory.getInstance("RSA");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(buffer);
            return (RSAPublicKey) keyFactory.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException e) {
            throw new BaseBusinessException("无此算法", e);
        } catch (InvalidKeySpecException e) {
            throw new BaseBusinessException("公钥非法", e);
        } catch (NullPointerException e) {
            throw new BaseBusinessException("公钥数据为空", e);
        } catch (NoSuchProviderException e) {
            throw new BaseBusinessException("no such provider: RSA, BC", e);
        }
    }

    /**
     * 从文件中加载密钥
     *
     * @param fileName 公钥或私钥的文件名
     * @return 字符串形式的密钥，去除-----BEGIN (.*)-----等多余信息
     */
    public static String loadKeyFromFile(String fileName) {
        byte[] keyBytes = loadRawKeyFromFile(fileName);

        // convert to der format
        return getBase64Key(new String(keyBytes));
    }

    /**
     * 从文件中加载密钥
     *
     * @param fileName 公钥或私钥的文件名
     * @return 字符串形式的密钥
     */
    public static byte[] loadRawKeyFromFile(String fileName) {
        InputStream resourceAsStream = RSAUtil.class.getClassLoader().getResourceAsStream(fileName);
        DataInputStream dis = new DataInputStream(resourceAsStream);
        byte[] keyBytes = null;
        try {
            keyBytes = new byte[resourceAsStream.available()];
            dis.readFully(keyBytes);
            dis.close();
        } catch (IOException e) {
            throw new BaseBusinessException("Failed to load public key from file '" + fileName + "'", e);
        }
        return keyBytes;
    }

    /**
     * 从流中加载密钥
     *
     * @param inputSteam 文件流
     * @return 密钥字符串
     */
    @SuppressWarnings("deprecation")
    public static String loadRawKey(InputStream inputSteam) {
        try {
            return IOUtils.toString(inputSteam);
        } catch (IOException e) {
            throw new BaseBusinessException(e.getMessage(), e);
        }
    }

    public static RSAPrivateKey loadPrivateKeyByStr(String privateKeyStr) {
        try {
            byte[] buffer = Base64.getDecoder().decode(privateKeyStr);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(buffer);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA", "BC"); // KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException e) {
            throw new BaseBusinessException("无此算法", e);
        } catch (InvalidKeySpecException e) {
            throw new BaseBusinessException("私钥非法", e);
        } catch (NullPointerException e) {
            throw new BaseBusinessException("私钥数据为空", e);
        } catch (NoSuchProviderException e) {
            throw new BaseBusinessException("no such provider: RSA, BC", e);
        }
    }

    /**
     * 公钥加密过程
     *
     * @param publicKey     公钥
     * @param plainTextData 明文数据
     * @return 密文
     */
    public static byte[] encrypt(RSAPublicKey publicKey, byte[] plainTextData) {
        if (publicKey == null) {
            throw new BaseBusinessException("加密公钥为空");
        }
        Cipher cipher = null;
        try {
            // 使用默认RSA
            cipher = Cipher.getInstance("RSA");
            // cipher= Cipher.getInstance("RSA", new BouncyCastleProvider());
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] output = cipher.doFinal(plainTextData);
            return output;
        } catch (NoSuchAlgorithmException e) {
            throw new BaseBusinessException("无此加密算法", e);
        } catch (NoSuchPaddingException e) {
            throw new BaseBusinessException("无此加密算法", e);
        } catch (InvalidKeyException e) {
            throw new BaseBusinessException("加密公钥非法,请检查", e);
        } catch (IllegalBlockSizeException e) {
            throw new BaseBusinessException("明文长度非法", e);
        } catch (BadPaddingException e) {
            throw new BaseBusinessException("明文数据已损坏", e);
        }
    }

    /**
     * 私钥加密过程
     *
     * @param privateKey    私钥
     * @param plainTextData 明文数据
     * @return 密文
     * @throws Exception 加密过程中的异常信息
     */
    public static byte[] encrypt(RSAPrivateKey privateKey, byte[] plainTextData) throws Exception {
        if (privateKey == null) {
            throw new BaseBusinessException("加密私钥为空");
        }
        Cipher cipher = null;
        try {
            // 使用默认RSA
            cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");// Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, privateKey);
            byte[] output = cipher.doFinal(plainTextData);
            return output;
        } catch (NoSuchAlgorithmException e) {
            throw new BaseBusinessException("无此加密算法", e);
        } catch (NoSuchPaddingException e) {
            throw new BaseBusinessException("系统中无此填充机制", e);
        } catch (InvalidKeyException e) {
            throw new BaseBusinessException("加密私钥非法,请检查", e);
        } catch (IllegalBlockSizeException e) {
            throw new BaseBusinessException("明文长度非法", e);
        } catch (BadPaddingException e) {
            throw new BaseBusinessException("明文数据已损坏", e);
        }
    }

    /**
     * 私钥解密过程
     *
     * @param privateKey 私钥
     * @param cipherData 密文数据
     * @return 明文
     */
    public static byte[] decrypt(RSAPrivateKey privateKey, byte[] cipherData) {
        if (privateKey == null) {
            throw new BaseBusinessException("解密私钥为空");
        }
        Cipher cipher = null;
        try {
            // 使用默认RSA
            cipher = Cipher.getInstance("RSA");
            // cipher= Cipher.getInstance("RSA", new BouncyCastleProvider());
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] output = cipher.doFinal(cipherData);
            return output;
        } catch (NoSuchAlgorithmException e) {
            throw new BaseBusinessException("无此解密算法", e);
        } catch (NoSuchPaddingException e) {
            throw new BaseBusinessException("系统中无此填充机制", e);
        } catch (InvalidKeyException e) {
            throw new BaseBusinessException("解密私钥非法", e);
        } catch (IllegalBlockSizeException e) {
            throw new BaseBusinessException("密文长度非法");
        } catch (BadPaddingException e) {
            throw new BaseBusinessException("密文数据已损坏", e);
        }
    }

    /**
     * 公钥解密过程
     *
     * @param publicKey  公钥
     * @param cipherData 密文数据
     * @return 明文
     * @throws Exception 解密过程中的异常信息
     */
    public static byte[] decrypt(RSAPublicKey publicKey, byte[] cipherData) throws Exception {
        if (publicKey == null) {
            throw new BaseBusinessException("解密公钥为空");
        }
        Cipher cipher = null;
        try {
            // 使用默认RSA
            cipher = Cipher.getInstance("RSA");
            // cipher= Cipher.getInstance("RSA", new BouncyCastleProvider());
            cipher.init(Cipher.DECRYPT_MODE, publicKey);
            byte[] output = cipher.doFinal(cipherData);
            return output;
        } catch (NoSuchAlgorithmException e) {
            throw new BaseBusinessException("无此解密算法", e);
        } catch (NoSuchPaddingException e) {
            throw new BaseBusinessException("系统中无此填充机制", e);
        } catch (InvalidKeyException e) {
            throw new BaseBusinessException("解密公钥非法", e);
        } catch (IllegalBlockSizeException e) {
            throw new BaseBusinessException("密文长度非法");
        } catch (BadPaddingException e) {
            throw new BaseBusinessException("密文数据已损坏");
        }
    }

}