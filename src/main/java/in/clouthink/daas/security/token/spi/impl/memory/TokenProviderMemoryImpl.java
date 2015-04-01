package in.clouthink.daas.security.token.spi.impl.memory;

import in.clouthink.daas.security.token.core.Token;
import in.clouthink.daas.security.token.spi.TokenProvider;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TokenProviderMemoryImpl implements TokenProvider<Token> {
    
    public static final Log logger = LogFactory.getLog(TokenProviderMemoryImpl.class);
    
    private Map<String, Token> tokenTokenMap = new ConcurrentHashMap<String, Token>();
    
    @Override
    public void saveToken(Token token) {
        logger.debug(String.format("Put token:%s expiredAt:%s",
                                   token.getToken(),
                                   token.getExpiredDate()));
        tokenTokenMap.put(token.getToken(), token);
    }
    
    @Override
    public Token findByToken(String token) {
        logger.debug(String.format("Get token:%s", token));
        return tokenTokenMap.get(token);
    }
    
    @Override
    public void revokeToken(Token token) {
        logger.debug(String.format("Del token:%s", token));
        if (token == null) {
            return;
        }
        tokenTokenMap.remove(token.getToken());
    }
    
    @Scheduled(cron = "0 0/10 * * * ?")
    public void cleanExpiredToken() {
        logger.debug("Start to clean the expired token automatically.");
        Set<Token> tokens = new HashSet<Token>();
        for (Token token : tokenTokenMap.values()) {
            if (token.getExpiredDate().getTime() < System.currentTimeMillis()) {
                tokens.add(token);
            }
        }
        for (Token token : tokens) {
            tokenTokenMap.remove(token.getToken());
        }
    }
    
}