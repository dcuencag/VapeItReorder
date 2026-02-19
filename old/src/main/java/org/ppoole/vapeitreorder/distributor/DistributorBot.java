package org.ppoole.vapeitreorder.distributor;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import org.ppoole.vapeitreorder.dto.CartResultDto;
import org.ppoole.vapeitreorder.dto.ItemDto;

import java.util.List;

public interface DistributorBot {

    String getDistributorName();

    default Browser.NewContextOptions newContextOptions() {
        return new Browser.NewContextOptions();
    }

    CartResultDto run(Page page, List<ItemDto> items);
}
