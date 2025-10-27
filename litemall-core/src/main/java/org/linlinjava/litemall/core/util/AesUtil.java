package org.linlinjava.litemall.core.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加密工具类
 * 用于加密学号、姓名、身份证号等敏感信息
 * 
 * @author BMAD
 * @date 2025-10-27
 */
@Component
public class AesUtil {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    
    // 从配置文件读取密钥（application.yml 中的 sicau.aes.key）
    private static String SECRET_KEY;
    
    /**
     * 设置密钥（通过 Spring 注入）
     */
    @Value("${sicau.aes.key:sicau2025campustradingsystem!}")
    public void setSecretKey(String key) {
        SECRET_KEY = key;
    }
    
    /**
     * 加密
     * @param plaintext 明文
     * @return Base64 编码的密文（IV + 密文）
     * @throws Exception 加密异常
     */
    public String encrypt(String plaintext) throws Exception {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        // 生成随机 IV（初始化向量）
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        
        SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        // 将 IV 和密文拼接后返回（IV在前，密文在后）
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        
        return Base64.getEncoder().encodeToString(combined);
    }
    
    /**
     * 解密
     * @param ciphertext Base64 编码的密文（IV + 密文）
     * @return 明文
     * @throws Exception 解密异常
     */
    public String decrypt(String ciphertext) throws Exception {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        
        byte[] combined = Base64.getDecoder().decode(ciphertext);
        
        // 提取 IV 和密文
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);
        
        SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        
        byte[] plaintext = cipher.doFinal(encrypted);
        return new String(plaintext, StandardCharsets.UTF_8);
    }
    
    /**
     * 批量加密（用于批量处理学号等）
     * @param plaintexts 明文数组
     * @return 密文数组
     */
    public String[] encryptBatch(String[] plaintexts) throws Exception {
        if (plaintexts == null) {
            return null;
        }
        
        String[] results = new String[plaintexts.length];
        for (int i = 0; i < plaintexts.length; i++) {
            results[i] = encrypt(plaintexts[i]);
        }
        return results;
    }
    
    /**
     * 批量解密
     * @param ciphertexts 密文数组
     * @return 明文数组
     */
    public String[] decryptBatch(String[] ciphertexts) throws Exception {
        if (ciphertexts == null) {
            return null;
        }
        
        String[] results = new String[ciphertexts.length];
        for (int i = 0; i < ciphertexts.length; i++) {
            results[i] = decrypt(ciphertexts[i]);
        }
        return results;
    }
}
