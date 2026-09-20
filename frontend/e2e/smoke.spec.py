import asyncio
import os
import tempfile
from pathlib import Path
from playwright.async_api import async_playwright


async def main() -> None:
    base_url = os.environ.get("BASE_URL", "http://localhost:4200")
    console_errors: list[str] = []
    responses: list[str] = []
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        page = await browser.new_page(viewport={"width": 1440, "height": 1000})
        page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
        page.on("response", lambda response: responses.append(f"{response.status} {response.url}") if "/api/" in response.url or "/ws/" in response.url else None)
        await page.goto(f"{base_url}/", wait_until="domcontentloaded")
        assert await page.get_by_text("POKER NIGHT").count() >= 1
        await page.get_by_placeholder("Enter display name").fill("Browser Host")
        await page.get_by_role("button", name="HOST A NEW TABLE").click()
        try:
            await page.get_by_text("LIVE TABLE").wait_for(timeout=10_000)
        except Exception:
            print("body:", (await page.locator("body").inner_text())[:2000])
            print("responses:", responses)
            print("console:", console_errors)
            raise
        table_code = (await page.locator(".table-meta strong").inner_text()).lstrip("#")
        guest = await browser.new_context(viewport={"width": 1440, "height": 1000})
        guest_page = await guest.new_page()
        await guest_page.goto(f"{base_url}/", wait_until="domcontentloaded")
        await guest_page.get_by_placeholder("Enter display name").fill("Browser Guest")
        await guest_page.get_by_placeholder("8-character code").fill(table_code)
        await guest_page.locator("select").select_option(index=2)
        await guest_page.get_by_role("button", name="JOIN THE TABLE").click()
        await guest_page.get_by_text("LIVE TABLE").wait_for(timeout=10_000)
        assert await page.locator(".seat-chip").count() >= 2
        dealer_start = page.locator("button.next-hand:not([disabled])")
        guest_dealer_start = guest_page.locator("button.next-hand:not([disabled])")
        for _ in range(20):
            if await dealer_start.count() or await guest_dealer_start.count():
                break
            await page.wait_for_timeout(250)
        assert await dealer_start.count() + await guest_dealer_start.count() == 1
        dealer_page = page if await dealer_start.count() else guest_page
        await dealer_page.locator(".variant-select").select_option("SWAP")
        await page.wait_for_timeout(700)
        assert await page.locator(".variant-select").input_value() == "SWAP"
        assert await guest_page.locator(".variant-select").input_value() == "SWAP"
        await dealer_page.locator("button.next-hand:not([disabled])").click()
        await page.locator(".dealing-note").wait_for(timeout=10_000)
        await guest_page.locator(".dealing-note").wait_for(timeout=10_000)
        assert await page.locator(".turn-card.your-turn").count() + await guest_page.locator(".turn-card.your-turn").count() == 1
        assert await page.locator(".action-bar button:disabled").count() + await guest_page.locator(".action-bar button:disabled").count() > 0
        assert "DEALER" in await page.locator(".table-info").inner_text()
        assert "TURN" in await page.locator(".table-info").inner_text() or "TURN" in await guest_page.locator(".table-info").inner_text()
        await page.screenshot(path="artifacts/dealer-selection-started.png", full_page=True)
        await page.get_by_role("button", name="SHARE INVITE").click()
        assert await page.locator(".invite-popover").count() == 1
        turn_page = page if await page.locator(".turn-card.your-turn").count() else guest_page
        await turn_page.get_by_role("button", name="VIEW PRIVATE CARDS").click()
        assert await page.locator(".invite-popover").count() == 0
        await guest_page.reload(wait_until="domcontentloaded")
        await guest_page.get_by_text("LIVE TABLE").wait_for(timeout=10_000)
        await page.screenshot(path=str(Path(tempfile.gettempdir()) / "poker-night-table.png"), full_page=True)
        assert not console_errors, f"Browser console errors: {console_errors}"
        print("browser-smoke: PASS")
        await guest.close()
        await browser.close()


asyncio.run(main())
