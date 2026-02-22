package org.ppoole.vapeitreorder.distributor;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import org.ppoole.vapeitreorder.dto.ItemDto;
import org.ppoole.vapeitreorder.dto.PriceOptionDto;

import java.util.Optional;

public interface DistributorPriceBot {

    String getDistributorName();

    default Browser.NewContextOptions newContextOptions() {
        return new Browser.NewContextOptions();
    }

    Optional<PriceOptionDto> searchProduct(Page page, ItemDto item);
}
