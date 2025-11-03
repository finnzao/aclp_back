package br.jus.tjba.aclp.security;

public interface TokenBlacklistService {

    void blacklist(String token, long expirationMillis);

    boolean isBlacklisted(String token);
}