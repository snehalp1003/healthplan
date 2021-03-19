/**
 * 
 */
package com.me.healthplan.utility;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * @author Snehal Patel
 */
public class JwtKeys {
    
    private PublicKey publicKey;
    private PrivateKey privateKey;

    public JwtKeys(PublicKey publicKey, PrivateKey privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }
}
