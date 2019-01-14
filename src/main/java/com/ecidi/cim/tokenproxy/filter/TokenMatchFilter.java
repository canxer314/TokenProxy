package com.ecidi.cim.tokenproxy.filter;

import com.ecidi.cim.tokenproxy.config.ProxyConfig;
import com.ecidi.cim.tokenproxy.config.ProxyConfig.UriFilterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TokenMatchFilter {
    private String[] allowPattern;
    private String[] authcPattern;

    TokenMatchFilter(ProxyConfig proxyConfig) {
        UriFilterConfig uriPatterns = proxyConfig.getTokenUriPattern();
        allowPattern = uriPatterns.getAllow();
        authcPattern = uriPatterns.getAuthc();
    }

    public boolean isUriAllow(String uri) {
        for (String regex : allowPattern) {
            if (uri.matches(regex)) {
                return true;
            }
        }
        return false;
    }

    public boolean isUriAuthc(String uri) {
        for (String regex : authcPattern) {
            if (uri.matches(regex)) {
                return true;
            }
        }
        return false;
    }
}
