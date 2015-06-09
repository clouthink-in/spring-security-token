package in.clouthink.daas.security.token.annotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.Filter;

import in.clouthink.daas.security.token.support.i18n.DefaultMessageProvider;
import in.clouthink.daas.security.token.support.i18n.MessageProvider;
import in.clouthink.daas.security.token.support.web.*;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.filter.CompositeFilter;

import in.clouthink.daas.security.token.configure.TokenConfigurer;
import in.clouthink.daas.security.token.configure.TokenConfigurerAdapter;
import in.clouthink.daas.security.token.configure.UrlAclProviderBuilder;
import in.clouthink.daas.security.token.core.*;
import in.clouthink.daas.security.token.core.acl.AccessRequestRoleVoter;
import in.clouthink.daas.security.token.core.acl.AccessRequestUserVoter;
import in.clouthink.daas.security.token.spi.*;
import in.clouthink.daas.security.token.spi.impl.DefaultUrlAuthorizationProvider;
import in.clouthink.daas.security.token.spi.impl.TokenAuthenticationProvider;
import in.clouthink.daas.security.token.spi.impl.UsernamePasswordAuthenticationProvider;
import in.clouthink.daas.security.token.spi.impl.memory.IdentityProviderMemoryImpl;
import in.clouthink.daas.security.token.spi.impl.memory.TokenProviderMemoryImpl;

@Configuration
public class TokenConfiguration implements ImportAware, BeanFactoryAware {
    
    public static final String DAAS_TOKEN_FILTER = "daasTokenFilter";
    
    protected ListableBeanFactory beanFactory;
    
    protected BeanDefinitionRegistry beanDefinitionRegistry;
    
    protected AnnotationAttributes enableToken;
    
    protected TokenConfigurer tokenConfigurer = new TokenConfigurerAdapter();
    
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = (ListableBeanFactory) beanFactory;
        this.beanDefinitionRegistry = (BeanDefinitionRegistry) beanFactory;
    }
    
    @Autowired(required = false)
    void setConfigurers(Collection<TokenConfigurer> configurers) {
        if (CollectionUtils.isEmpty(configurers)) {
            return;
        }
        if (configurers.size() > 1) {
            throw new IllegalStateException("Only one TokenConfigurer may exist");
        }
        TokenConfigurer configurer = configurers.iterator().next();
        this.tokenConfigurer = configurer;
    }
    
    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        this.enableToken = AnnotationAttributes.fromMap(importMetadata.getAnnotationAttributes(EnableToken.class.getName(),
                                                                                               false));
        Assert.notNull(this.enableToken,
                       "@EnableJwt is not present on importing class " + importMetadata.getClassName());
    }
    
    @Bean(name = DAAS_TOKEN_FILTER)
    @Autowired
    public CompositeFilter daasTokenCompositeFilter(AuthenticationFilter authenticationFilter,
                                                    AuthorizationFilter authorizationFilter,
                                                    LoginEndpoint loginEndpoint,
                                                    LogoutEndpoint logoutEndpoint) {
        List<Filter> filters = new ArrayList<Filter>();
        tokenConfigurer.configure(authenticationFilter);
        tokenConfigurer.configure(authorizationFilter);
        tokenConfigurer.configure(loginEndpoint);
        tokenConfigurer.configure(logoutEndpoint);
        filters.add(loginEndpoint);
        filters.add(logoutEndpoint);
        filters.add(authenticationFilter);
        filters.add(authorizationFilter);
        CompositeFilter result = new CompositeFilter();
        result.setFilters(filters);
        return result;
    }
    
    @Bean
    @Autowired
    @DependsOn("daasDefaultAuthenticationManager")
    public AuthenticationFilter daasTokenAuthenticationFilter(AuthenticationManager authenticationManager,
                                                              MessageProvider messageProvider) {
        AuthenticationFilter result = new AuthenticationFilter();
        result.setAuthenticationManager(authenticationManager);
        result.setAuthorizationFailureHandler(new DefaultAuthorizationFailureHandler(messageProvider));
        return result;
    }
    
    @Bean
    @Autowired
    @DependsOn("daasDefaultAuthorizationManager")
    public AuthorizationFilter daasTokenAuthorizationFilter(AuthorizationManager authorizationManager,
                                                            MessageProvider messageProvider) {
        AuthorizationFilter result = new AuthorizationFilter();
        result.setAuthorizationManager(authorizationManager);
        result.setAuthorizationFailureHandler(new DefaultAuthorizationFailureHandler(messageProvider));
        return result;
    }
    
    @Bean
    @Autowired
    @DependsOn("daasDefaultAuthenticationManager")
    public LoginEndpoint daasTokenLoginEndpoint(AuthenticationManager authenticationManager,
                                                MessageProvider messageProvider) {
        LoginEndpoint result = new LoginEndpoint();
        result.setAuthenticationManager(authenticationManager);
        result.setAuthenticationFailureHandler(new DefaultAuthenticationFailureHandler(messageProvider));
        return result;
    }
    
    @Bean
    @Autowired
    @DependsOn("daasDefaultAuthenticationManager")
    public LogoutEndpoint daasTokenLogoutEndpoint(AuthenticationManager authenticationManager,
                                                  MessageProvider messageProvider) {
        LogoutEndpoint result = new LogoutEndpoint();
        result.setAuthenticationManager(authenticationManager);
        result.setAuthorizationFailureHandler(new DefaultAuthorizationFailureHandler(messageProvider));
        return result;
    }
    
    @Bean
    @Autowired
    @DependsOn({ "daasUsernamePasswordAuthenticationProvider",
                "daasTokenAuthenticationProvider" })
    public AuthenticationManager daasDefaultAuthenticationManager(IdentityProvider identityProvider,
                                                                  TokenManager tokenManager) {
        DefaultAuthenticationManager result = new DefaultAuthenticationManager();
        result.addProvider(daasUsernamePasswordAuthenticationProvider(identityProvider,
                                                                      tokenManager));
        result.addProvider(daasTokenAuthenticationProvider(identityProvider,
                                                           tokenManager));
        return result;
    }
    
    @Bean
    @Autowired
    public AuthorizationManager daasDefaultAuthorizationManager(AuthorizationProvider authorizationProvider) {
        DefaultAuthorizationManager result = new DefaultAuthorizationManager();
        result.getProviders().add(authorizationProvider);
        return result;
    }
    
    @Bean
    @Autowired
    public AuthenticationProvider daasUsernamePasswordAuthenticationProvider(IdentityProvider identityProvider,
                                                                             TokenManager tokenManager) {
        UsernamePasswordAuthenticationProvider result = new UsernamePasswordAuthenticationProvider();
        result.setIdentityProvider(identityProvider);
        result.setTokenManager(tokenManager);
        return result;
    }
    
    @Bean
    @Autowired
    public AuthenticationProvider daasTokenAuthenticationProvider(IdentityProvider identityProvider,
                                                                  TokenManager tokenManager) {
        TokenAuthenticationProvider result = new TokenAuthenticationProvider();
        result.setIdentityProvider(identityProvider);
        result.setTokenManager(tokenManager);
        return result;
    }
    
    @Bean
    @Autowired
    public AuthorizationProvider daasDefaultUrlAuthorizationProvider(AclProvider aclProvider) {
        DefaultUrlAuthorizationProvider result = new DefaultUrlAuthorizationProvider();
        result.getVoters().add(new AccessRequestRoleVoter());
        result.getVoters().add(new AccessRequestUserVoter());
        result.setProvider(aclProvider);
        return result;
    }
    
    @Bean
    @Autowired
    public TokenManager daasDefaultTokenManager(TokenProvider tokenProvider) {
        DefaultTokenManager tokenManager = new DefaultTokenManager();
        tokenManager.setTokenProvider(tokenProvider);
        tokenConfigurer.configure(tokenManager);
        return tokenManager;
    }
    
    @Bean
    public AclProvider daasDefaultUrlAclProvider() {
        UrlAclProviderBuilder urlAclProviderBuilder = UrlAclProviderBuilder.newInstance();
        tokenConfigurer.configure(urlAclProviderBuilder);
        return urlAclProviderBuilder.build();
    }
    
    @Bean
    public TokenProvider daasDefaultTokenProvider() {
        return new TokenProviderMemoryImpl();
    }
    
    @Bean
    public IdentityProvider daasDefaultIdentityProvider() {
        return new IdentityProviderMemoryImpl();
    }
    
    @Bean
    public MessageProvider messageProvider() {
        MessageProvider result = new DefaultMessageProvider();
        tokenConfigurer.configure(result);
        return result;
    }
    
}
