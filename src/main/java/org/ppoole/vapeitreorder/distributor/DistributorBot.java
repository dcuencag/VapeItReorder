package org.ppoole.vapeitreorder.distributor;

import com.microsoft.playwright.Page;
import org.ppoole.vapeitreorder.dto.CartResultDto;
import org.ppoole.vapeitreorder.dto.ItemDto;

import java.util.List;

public interface DistributorBot {

    String getDistributorName();

    CartResultDto run(Page page, List<ItemDto> items);
}
