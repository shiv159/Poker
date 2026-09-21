import asyncio
import os
from pathlib import Path

from playwright.async_api import async_playwright


async def wait_for_one_enabled(page_a, page_b, selector, timeout=15000):
    for _ in range(timeout // 250):
        counts = [await page_a.locator(selector).count(), await page_b.locator(selector).count()]
        if sum(counts) == 1:
            return page_a if counts[0] else page_b
        await page_a.wait_for_timeout(250)
    raise AssertionError(f"expected one enabled {selector}")


async def main():
    base_url = os.environ.get("BASE_URL", "http://localhost:4200")
    console_errors = []
    Path("artifacts").mkdir(exist_ok=True)
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        host_context = await browser.new_context(viewport={"width": 1440, "height": 1000})
        guest_context = await browser.new_context(viewport={"width": 1440, "height": 1000})
        host = await host_context.new_page()
        guest = await guest_context.new_page()
        for page in (host, guest):
            page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)

        await host.goto(base_url, wait_until="domcontentloaded")
        await host.get_by_placeholder("Enter display name").fill("Flow Host")
        await host.get_by_role("button", name="HOST A NEW TABLE").click()
        await host.get_by_text("LIVE TABLE").wait_for(timeout=10000)
        table_code = (await host.locator(".table-meta strong").inner_text()).lstrip("#")

        await guest.goto(base_url, wait_until="domcontentloaded")
        await guest.get_by_placeholder("Enter display name").fill("Flow Guest")
        await guest.get_by_placeholder("8-character code").fill(table_code)
        await guest.get_by_role("button", name="JOIN THE TABLE").click()
        await guest.get_by_text("LIVE TABLE").wait_for(timeout=10000)

        dealer = await wait_for_one_enabled(host, guest, "button.next-hand:not([disabled])")
        await dealer.locator(".variant-select").select_option("CLASSIC")
        await host.wait_for_timeout(500)
        assert await host.locator(".variant-select").input_value() == "CLASSIC"
        assert await guest.locator(".variant-select").input_value() == "CLASSIC"
        await dealer.locator("button.next-hand:not([disabled])").click()
        await host.locator(".dealing-note").wait_for(timeout=10000)
        await guest.locator(".dealing-note").wait_for(timeout=10000)
        assert await host.locator(".my-cards pn-playing-card").count() == 3
        assert await guest.locator(".my-cards pn-playing-card").count() == 3
        assert await host.locator(".turn-card.your-turn").count() + await guest.locator(".turn-card.your-turn").count() == 1

        await host.get_by_role("button", name="SHARE INVITE").click()
        assert await host.locator(".invite-popover").count() == 1
        invite = await host.locator(".invite-popover input").input_value()
        assert f"table={table_code}" in invite
        await host.locator(".invite-open").click()
        await host.wait_for_timeout(200)
        assert await host.locator(".invite-popover").count() == 1

        turn_page = host if await host.locator(".turn-card.your-turn").count() else guest
        await turn_page.get_by_role("button", name="VIEW PRIVATE CARDS").click()
        assert await turn_page.locator(".invite-popover").count() == 0
        await turn_page.get_by_role("button", name="CHAAL").click()
        for _ in range(60):
            if await host.locator("button.fold:not([disabled])").count() + await guest.locator("button.fold:not([disabled])").count() == 1:
                break
            await host.wait_for_timeout(250)
        next_turn = host if await host.locator("button.fold:not([disabled])").count() else guest
        waiting_turn = guest if next_turn is host else host
        assert await waiting_turn.locator(".action-bar button:disabled").count() >= 4
        await next_turn.get_by_role("button", name="FOLD").click()
        await host.get_by_text("HAND COMPLETE", exact=True).wait_for(timeout=10000)
        await guest.get_by_text("HAND COMPLETE", exact=True).wait_for(timeout=10000)
        assert await host.locator(".result-banner strong").count() == 1
        next_dealer = await wait_for_one_enabled(host, guest, "button.next-hand:not([disabled])")
        await next_dealer.locator(".variant-select").select_option("FOUR_CARD")
        await host.wait_for_timeout(500)
        assert await guest.locator(".variant-select").input_value() == "FOUR_CARD"
        await next_dealer.locator("button.next-hand:not([disabled])").click()
        await host.locator(".dealing-note").wait_for(timeout=10000)
        assert await host.locator(".my-cards pn-playing-card").count() == 4
        assert await guest.locator(".my-cards pn-playing-card").count() == 4
        await host.screenshot(path="artifacts/full-flow-four-card.png", full_page=True)
        assert not console_errors, console_errors
        print("full-flow: PASS")
        await guest_context.close()
        await host_context.close()
        await browser.close()


asyncio.run(main())
