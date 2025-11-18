/**
 * Browser automation script to inspect Oxygen page styling
 * Uses Puppeteer to navigate, screenshot, and analyze the DOM
 */

const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

async function inspectPage(pageUrl) {
  console.log('\n=== Browser Inspection of Oxygen Page ===\n');
  console.log('URL:', pageUrl);
  console.log();

  let browser;
  try {
    // Launch browser
    console.log('Launching browser...');
    browser = await puppeteer.launch({
      headless: false, // Show browser for debugging
      args: ['--no-sandbox', '--disable-setuid-sandbox']
    });

    const page = await browser.newPage();
    await page.setViewport({ width: 1920, height: 1080 });

    // Navigate to page
    console.log('Navigating to page...');
    await page.goto(pageUrl, { waitUntil: 'networkidle2', timeout: 30000 });
    console.log('✅ Page loaded!\n');

    // Take screenshot
    const screenshotPath = path.join(__dirname, 'page-screenshot.png');
    await page.screenshot({ path: screenshotPath, fullPage: true });
    console.log('📸 Screenshot saved:', screenshotPath);
    console.log();

    // Check if CSS variables are resolved
    console.log('=== CSS Variables Check ===');
    const cssVarsResolved = await page.evaluate(() => {
      const body = document.body;
      const computed = window.getComputedStyle(body);

      // Get a test element (look for any element with primary color)
      const testElement = document.querySelector('[style*="var(--primary)"]') ||
                         document.querySelector('[class*="primary"]') ||
                         body;

      const testComputed = window.getComputedStyle(testElement);

      return {
        bodyBg: computed.backgroundColor,
        bodyColor: computed.color,
        primaryResolved: testComputed.getPropertyValue('--primary') || 'not found',
        foregroundResolved: testComputed.getPropertyValue('--foreground') || 'not found',
        // Try to get actual computed color
        actualColor: testComputed.color,
        actualBackground: testComputed.backgroundColor
      };
    });

    console.log('  Body background:', cssVarsResolved.bodyBg);
    console.log('  Body color:', cssVarsResolved.bodyColor);
    console.log('  --primary value:', cssVarsResolved.primaryResolved);
    console.log('  --foreground value:', cssVarsResolved.foregroundResolved);
    console.log('  Actual computed color:', cssVarsResolved.actualColor);
    console.log('  Actual computed background:', cssVarsResolved.actualBackground);
    console.log();

    // Check grid layout
    console.log('=== Grid Layout Check ===');
    const gridInfo = await page.evaluate(() => {
      const gridElements = Array.from(document.querySelectorAll('[style*="grid"], [style*="display: grid"], .grid'));

      return gridElements.map(el => {
        const computed = window.getComputedStyle(el);
        return {
          tag: el.tagName,
          display: computed.display,
          gridTemplateColumns: computed.gridTemplateColumns,
          gap: computed.gap,
          innerHTML: el.innerHTML.substring(0, 100) + '...'
        };
      });
    });

    console.log(`  Found ${gridInfo.length} grid elements:`);
    gridInfo.forEach((grid, idx) => {
      console.log(`  Grid ${idx + 1}:`);
      console.log('    Display:', grid.display);
      console.log('    Columns:', grid.gridTemplateColumns);
      console.log('    Gap:', grid.gap);
    });
    console.log();

    // Check spacing values
    console.log('=== Spacing Check ===');
    const spacingInfo = await page.evaluate(() => {
      const elements = Array.from(document.querySelectorAll('section, div, h1, h2, h3, p'));
      const samples = elements.slice(0, 10).map(el => {
        const computed = window.getComputedStyle(el);
        return {
          tag: el.tagName,
          className: el.className,
          padding: computed.padding,
          margin: computed.margin
        };
      });
      return samples;
    });

    spacingInfo.forEach((el, idx) => {
      console.log(`  Element ${idx + 1} (${el.tag}):`);
      console.log('    Padding:', el.padding);
      console.log('    Margin:', el.margin);
    });
    console.log();

    // Check typography
    console.log('=== Typography Check ===');
    const typographyInfo = await page.evaluate(() => {
      const elements = Array.from(document.querySelectorAll('h1, h2, h3, p, a, span'));
      const samples = elements.slice(0, 10).map(el => {
        const computed = window.getComputedStyle(el);
        return {
          tag: el.tagName,
          text: el.textContent.substring(0, 50),
          fontSize: computed.fontSize,
          fontWeight: computed.fontWeight,
          color: computed.color
        };
      });
      return samples;
    });

    typographyInfo.forEach((el, idx) => {
      console.log(`  ${el.tag}: "${el.text}"`);
      console.log('    Font size:', el.fontSize);
      console.log('    Font weight:', el.fontWeight);
      console.log('    Color:', el.color);
    });
    console.log();

    // Check for specific test content
    console.log('=== Content Verification ===');
    const content = await page.evaluate(() => {
      return {
        hasMeshStylingTest: !!document.querySelector('*:contains("Mesh Styling Test")') ||
                           document.body.textContent.includes('Mesh Styling Test'),
        hasGridLayoutTest: document.body.textContent.includes('Grid Layout Test'),
        hasSpacingTest: document.body.textContent.includes('Spacing Test'),
        hasTypographyTest: document.body.textContent.includes('Typography Scale Test'),
        fullText: document.body.textContent
      };
    });

    console.log('  "Mesh Styling Test" found:', content.hasMeshStylingTest);
    console.log('  "Grid Layout Test" found:', content.hasGridLayoutTest);
    console.log('  "Spacing Test" found:', content.hasSpacingTest);
    console.log('  "Typography Scale Test" found:', content.hasTypographyTest);
    console.log();

    // Get page HTML
    const html = await page.content();
    const htmlPath = path.join(__dirname, 'page-source.html');
    fs.writeFileSync(htmlPath, html);
    console.log('📄 HTML source saved:', htmlPath);
    console.log();

    // Summary
    console.log('=== SUMMARY ===');
    console.log('✅ Page loaded successfully');
    console.log('✅ Screenshot captured');
    console.log('✅ HTML source saved');
    console.log();
    console.log('📊 Results:');
    console.log('  - CSS Variables:', cssVarsResolved.primaryResolved !== 'not found' ? '✅ RESOLVED' : '❌ NOT RESOLVED');
    console.log('  - Grid Layout:', gridInfo.length > 0 ? `✅ FOUND (${gridInfo.length} grids)` : '❌ NOT FOUND');
    console.log('  - Content:', content.hasMeshStylingTest ? '✅ CORRECT' : '❌ MISSING');
    console.log();

    console.log('🎨 Verdict:',
      cssVarsResolved.primaryResolved !== 'not found' && gridInfo.length > 0 && content.hasMeshStylingTest
        ? '✅ STYLING WORKS! Ready for full Mesh import!'
        : '⚠️ Some issues found. Review screenshot and HTML source.');
    console.log();

  } catch (error) {
    console.error('❌ Error:', error.message);
    console.error(error.stack);
  } finally {
    if (browser) {
      await browser.close();
    }
  }
}

// Run inspection
const pageUrl = process.argv[2] || 'http://hbtcomputers.com.au.test/?page_id=49';
inspectPage(pageUrl);