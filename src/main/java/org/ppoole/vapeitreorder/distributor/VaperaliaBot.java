package org.ppoole.vapeitreorder.distributor;

import com.microsoft.playwright.Page;
import org.ppoole.vapeitreorder.dto.CartResultDto;
import org.ppoole.vapeitreorder.dto.ItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VaperaliaBot implements DistributorBot {

    private static final Logger log = LoggerFactory.getLogger(VaperaliaBot.class);

    private final String url;
    private final String username;
    private final String password;

    public VaperaliaBot(
            @Value("${vaperalia.url:}") String url,
            @Value("${vaperalia.username:}") String username,
            @Value("${vaperalia.password:}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public String getDistributorName() {
        return "vaperalia";
    }

    @Override
    public CartResultDto run(Page page, List<ItemDto> items) {
        log.warn("VaperaliaBot is not implemented yet");
        throw new UnsupportedOperationException("VaperaliaBot is not implemented yet");
    }
}
