import asyncio
import os
from playwright.async_api import async_playwright


async def main():
    base = os.environ.get("BASE_URL", "http://localhost:4200")
    errors = []
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        host_context = await browser.new_context(viewport={"width": 1440, "height": 1000})
        guest_context = await browser.new_context(viewport={"width": 1440, "height": 1000})
        host, guest = await host_context.new_page(), await guest_context.new_page()
        for page in (host, guest):
            page.on("console", lambda msg: errors.append(msg.text) if msg.type == "error" else None)
        await host.goto(base, wait_until="domcontentloaded")
        await host.get_by_placeholder("Enter display name").fill("Privacy Host")
        await host.get_by_role("button", name="HOST A NEW TABLE").click()
        await host.get_by_text("LIVE TABLE").wait_for(timeout=10000)
        code = (await host.locator(".table-meta strong").inner_text()).lstrip("#")
        await guest.goto(base, wait_until="domcontentloaded")
        await guest.get_by_placeholder("Enter display name").fill("Privacy Guest")
        await guest.get_by_placeholder("8-character code").fill(code)
        await guest.get_by_role("button", name="JOIN THE TABLE").click()
        await guest.get_by_text("LIVE TABLE").wait_for(timeout=10000)
        for _ in range(60):
            starts = [host.locator("button.next-hand:not([disabled])"), guest.locator("button.next-hand:not([disabled])")]
            counts = [await x.count() for x in starts]
            if sum(counts) == 1:
                dealer = host if counts[0] else guest
                break
            await host.wait_for_timeout(250)
        await dealer.locator(".variant-select").select_option("CLASSIC")
        await host.wait_for_timeout(500)
        for _ in range(40):
            if await host.locator("button.next-hand:not([disabled])").count() + await guest.locator("button.next-hand:not([disabled])").count() == 1:
                break
            await host.wait_for_timeout(250)
        dealer = host if await host.locator("button.next-hand:not([disabled])").count() else guest
        await dealer.locator("button.next-hand:not([disabled])").click()
        await host.locator(".dealing-note").wait_for(timeout=10000)
        await guest.locator(".dealing-note").wait_for(timeout=10000)
        assert await host.locator(".my-cards [aria-label='Hidden playing card']").count() == 3
        assert await guest.locator(".my-cards [aria-label='Hidden playing card']").count() == 3
        turn = host if await host.locator(".turn-card.your-turn").count() else guest
        await turn.get_by_role("button", name="View private hole cards").click()
        await turn.locator(".my-cards [aria-label$='of spades'], .my-cards [aria-label$='of hearts'], .my-cards [aria-label$='of diamonds'], .my-cards [aria-label$='of clubs']").first.wait_for(timeout=5000)
        assert await turn.locator(".my-cards [aria-label='Hidden playing card']").count() == 0
        pot_before = int((await host.locator(".pot strong").inner_text()).strip())
        await turn.get_by_role("button", name="CHAAL").click()
        for _ in range(40):
            if int((await host.locator(".pot strong").inner_text()).strip()) > pot_before:
                break
            await host.wait_for_timeout(250)
        assert int((await host.locator(".pot strong").inner_text()).strip()) > pot_before
        assert not errors, errors
        print("privacy-pot: PASS")
        await host_context.close()
        await guest_context.close()
        await browser.close()


asyncio.run(main())
