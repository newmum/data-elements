package com.linewell.dataelement.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SmUtil;
import com.google.common.collect.Lists;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 密码规则加密工具类
 */
public class PwdRuleUtil {

    /**
     * 弱密码正则
     */
    public static final String WEAK_PWD_REGEX = "^(?:\\d+|[a-zA-Z]+|[!@#$%^&*_=(),.<>]+).{6,30}$";
    /**
     * 中等密码正则
     */
    public static final String MEDIUM_PWD_REGEX = "^(?![a-zA-z]+$)(?!\\d+$)(?![!@#$%^&*_=()],.<>+$)[a-zA-Z\\d!@#$%^&*_=(),.<>].{7,30}+$";
    /**
     * 强密码正则
     */
    public static final String STRONG_PWD_REGEX = "^(?![a-zA-z]+$)(?!\\d+$)(?![!@#$%^&*_=()],.<>+$)(?![a-zA-z\\d]+$)(?![a-zA-z!@#$%^&*_=()],.<>+$)(?![\\d!@#$%^&*_=()],.<>+$)[a-zA-Z\\d!@#$%^&*_=(),.<>].{7,30}+$";

    /**
     * 密码允许的字符
     */
    private static String[] codeArr = {"abdefghjkmnqrtwxy", "ABDEFGHJKMNQRTWXY", "123456789", "!@#=+-"};

    public static final String FIXED_SALT = "4cfbf300-cea4-4a98-8096-37fbf28a5d11";//固定盐


    /**
     * @description:明文密码加密（uuid加盐）
     * @param: plaintextPassword 明文密码
     * @param: rule 加密规则
     * @return: java.lang.String
     * @author: ljixuan
     * @date: 2022/8/19 16:07
     */
    public static String encryptPwdBySalt(String plaintextPassword, String... rule) {
        String uuid = UUID.randomUUID().toString();
        if (BooleanUtil.isTrue(getOpenFixedSalt())) {
            uuid = FIXED_SALT;
            //明文密码需要先sm3加密 FIXME 用长度的方法判断是否是密文
            if (StrUtil.length(plaintextPassword) < 30) {
                plaintextPassword = SmUtil.sm3(plaintextPassword).toUpperCase();
            } else {
                plaintextPassword = plaintextPassword.toUpperCase();
            }
        }
        String[] uuidArr = uuid.toUpperCase().split("-");
        String passwordAppendUUID = new StringBuilder(uuidArr[0]).append(uuidArr[1]).append(plaintextPassword).append(uuidArr[3]).append(uuidArr[4]).toString();
        return new StringBuilder(uuidArr[4]).append(uuidArr[0]).append(uuidArr[2]).append(encrypt(passwordAppendUUID).toUpperCase()).append(uuidArr[1]).append(uuidArr[3]).toString();
    }


    /**
     * @description:用于前端rsa加密后的密码，先进行Rsa解密再加盐加密，返回存储到数据库中的密码
     * @param: rsaPwd
     * @param: rule
     * @return: java.lang.String
     * @author: ljixuan
     * @date: 2022/8/25 11:58
     */
    public static String encryptRsaPwdBySalt(String rsaPwd, String rule) {
        // rsa解密
        byte[] cipherData = Base64.getDecoder().decode(rsaPwd);
        String privateKeyStr = RSAUtil.loadKeyFromFile("rsaPrivateKey.key");
        byte[] xpwd = RSAUtil.decrypt(RSAUtil.loadPrivateKeyByStr(privateKeyStr), cipherData);
        // 加密
        return encryptPwdBySalt(new String(xpwd), rule);
    }

    /**
     * @description:Rsa加密后，解密成明文（前端接收的密文专用）
     * @param: rsaPwd
     * @return: java.lang.String
     * @author: ljixuan
     * @date: 2022/8/25 13:58
     */
    public static String decryptRsaPwd(String rsaPwd) {
        String privateKeyStr = RSAUtil.loadKeyFromFile("rsaPrivateKey.key");
        return decryptRsaPwd(rsaPwd, privateKeyStr);
    }

    /**
     * @description:自定义加密密码 1、通过配置文件获取默认密码
     * 2、RSA解密成明文
     * 3、通过配置文件获取加密规则加密
     */
    public static String customerdecryptPassword(String userDefaultPassword, String rule) {
        String newUserDefaultPassword = userDefaultPassword.replaceAll(" +", "+");
        String privateKeyStr = RSAUtil.loadKeyFromFile("rsaPrivateKey.key");
        String cleartextPasswords = decryptRsaPwd(newUserDefaultPassword, privateKeyStr); //明文密码
        return encryptPwdBySalt(cleartextPasswords, rule);
    }

    /**
     * @description:Rsa加密后，解密成明文
     * @param: rsaPwd
     * @param: privateKey
     * @return: java.lang.String
     * @author: ljixuan
     * @date: 2022/8/25 13:58
     */
    public static String decryptRsaPwd(String rsaPwd, String privateKey) {
        // rsa解密
        byte[] cipherData = Base64.getDecoder().decode(rsaPwd);
        byte[] xpwd = RSAUtil.decrypt(RSAUtil.loadPrivateKeyByStr(privateKey), cipherData);
        return new String(xpwd);
    }

    /**
     * @description:校验Rsa加密后的密码，与数据库中的密码是否一致（uuid加盐）
     * @param: rsaPassword  前端rsa加密后的密码
     * @param: encryptPassword 数据库中的密码
     * @param: rule
     * @return: boolean
     * @author: ljixuan
     * @date: 2022/8/25 11:32
     */
    public static boolean checkRsaPwdBySalt(String rsaPassword, String encryptPassword, String rule) {
        try {
            return checkPwdBySalt(decryptRsaPwd(rsaPassword), encryptPassword, rule);
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * @description:校验密码是否一致（uuid加盐）
     * @param: plaintextPassword  明文密码
     * @param: encryptPassword  数据库中存储的密码
     * @param: rule 加密规则
     * @return: boolean
     * @author: ljixuan
     * @date: 2022/8/19 16:37
     */
    public static boolean
    checkPwdBySalt(String plaintextPassword, String encryptPassword, String... rule) {
//        if (BooleanUtil.isTrue(getOpenFixedSalt())) {
//            //明文密码需要先sm3加密 FIXME 用长度的方法判断是否是密文
//            if (StrUtil.length(plaintextPassword) < 30) {
//                plaintextPassword = SmUtil.sm3(plaintextPassword).toUpperCase();
//            } else {
//                plaintextPassword = plaintextPassword.toUpperCase();
//            }
//        }
        int length = StrUtil.length(encryptPassword);
        try {
            String[] uuidArr = {
                    StrUtil.sub(encryptPassword, 12, 20),//8
                    StrUtil.sub(encryptPassword, length - 4 - 4, length - 4),//4
                    StrUtil.sub(encryptPassword, 20, 24),//4
                    StrUtil.sub(encryptPassword, length - 4, length),//4
                    StrUtil.sub(encryptPassword, 0, 12)//12
            };
            String oldPassword = StrUtil.sub(encryptPassword, 24, length - 4 - 4);
            String inputPassword = new StringBuilder(uuidArr[0]).append(uuidArr[1]).append(plaintextPassword).append(uuidArr[3]).append(uuidArr[4]).toString();
            if (StrUtil.equalsIgnoreCase(oldPassword, encrypt(inputPassword))) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    /**
     * 根据规则对密码进行加密
     * 注意：这边的密码是解密过的明文密码
     *
     * @param pwd 明文密码
     * @return String string
     * @date 2019年10月30日10 :37:21
     */
    @Deprecated //用户密码使用加盐加密方法
    public static String encrypt(String pwd) {
        return SmUtil.sm3(pwd).toUpperCase();
    }

    /**
     * @description:随机生成默认密码
     * @param: passwordLength 密码长度，长度最小不得小于8，如果小于8，生成的密码也是8位
     * @return: java.lang.String
     * @author: ljixuan
     * @date: 2022/3/25 17:47
     */
    public static String randomGeneratorPassword(int passwordLength) {
        int defaultLength = 8;
        List<String> passwordChars = Lists.newArrayList();
        //每个字符至少一个
        passwordChars.add(RandomUtil.randomString(codeArr[0], 1));
        passwordChars.add(RandomUtil.randomString(codeArr[1], 1));
        passwordChars.add(RandomUtil.randomString(codeArr[2], 1));
        passwordChars.add(RandomUtil.randomString(codeArr[3], 1));
        if (passwordLength < defaultLength) {
            passwordLength = defaultLength;
        }
        //补齐剩下长度的字符
        for (int i = 0; i < passwordLength - 4; i++) {
            int index = RandomUtil.randomInt(0, 4);
            passwordChars.add(RandomUtil.randomString(codeArr[index], 1));
        }
        Collections.shuffle(passwordChars);
        return StrUtil.join("", passwordChars).replace(" ", "");
    }

    //固定盐值，默认开启
    private static boolean getOpenFixedSalt() {
        return true;
    }

//    public static void main(String[] args) {
//        System.out.println(SmUtil.sm3("Lwyf2024!@#").toUpperCase());
//        System.out.println(encryptPwdBySalt("Linewell.*#2023"));
//        System.out.println(checkPwdBySalt("10B342FBAE8A32D5846C23A3FAD84D74EEE8493FA43C78172CB660A835339B20", encryptPwdBySalt("Lwyf2024!@#")));
//
//    }
}
