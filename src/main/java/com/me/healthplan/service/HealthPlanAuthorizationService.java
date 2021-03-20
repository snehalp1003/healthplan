/**
 * 
 */
package com.me.healthplan.service;

import java.text.ParseException;
import java.util.Date;

import org.springframework.stereotype.Service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * @author Snehal Patel
 */

@Service
public class HealthPlanAuthorizationService {
    
    static RSAKey rsaPublicJWK = null;
    static RSAKey rsaJWK = null;
    static {
         try {

             // RSA signatures require a public and private RSA key pair, the public key
             // must be made known to the JWS recipient in order to verify the signatures
             rsaJWK = new RSAKeyGenerator(2048)
                     .keyID("123")
                     .generate();
             rsaPublicJWK = rsaJWK.toPublicJWK();

         } catch (JOSEException e) {
             e.printStackTrace();
         }

     }
    
    public String generateToken() throws JOSEException {


        // Create RSA-signer with the private key
        JWSSigner signer = new RSASSASigner(rsaJWK);

        // Prepare JWT with claims set
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().issueTime(new Date(System.currentTimeMillis()))
                .expirationTime(new Date(System.currentTimeMillis() + 1000 * 60 * 10))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJWK.getKeyID()).build(),
                claimsSet);

        // Compute the RSA signature
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }
    
    public String authorize(String authorization) {

        if (!authorization.contains("Bearer ")) {
            return "Improper Format of Token";
        }

        String token = authorization.split(" ")[1];

            try {

                SignedJWT signedJWT = SignedJWT.parse(token);
                JWSVerifier verifier = new RSASSAVerifier(this.rsaPublicJWK);

                // token is not valid
                if(!signedJWT.verify(verifier)){
                    return "Invalid Token";
                }

                Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();

                // token ttl has expired
                if(new Date().after(expirationTime)) {
                    return "Token has expired";
                }
            } catch (JOSEException | ParseException e ){
                System.out.println(e.getMessage());
                return "Valid Token";
            }

        return "Valid Token";
    }
}
