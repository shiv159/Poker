import asyncio
from playwright.async_api import async_playwright


async def main() -> None:
    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(headless=True)
        page = await browser.new_page(viewport={"width": 1280, "height": 900})
        errors = []
        page.on("console", lambda message: errors.append(message.text) if message.type == "error" else None)
        await page.goto("http://localhost:4200/", wait_until="networkidle")
        assert await page.get_by_text("POKER NIGHT").count() >= 1
        assert await page.get_by_placeholder("Enter display name").count() == 1
        assert await page.get_by_role("button", name="JOIN THE TABLE").count() == 1
        await page.screenshot(path="artifacts/lobby-card-refactor.png", full_page=True)
        assert not errors, errors
        print("lobby-dom: PASS")
        await browser.close()


asyncio.run(main())
