import { expect, test } from '@plugwright/runner';

test('next button switches page in place', async ({ player }) => {
  player.chat('/vpaged');
  const gui = await player.gui({ title: /Entries/ });
  const indicator = gui.locator((i) => i.displayName.includes('Page'));
  await gui.locator((i) => i.displayName.includes('Next')).click();
  await expect.poll(() => indicator.displayName(), { timeout: 10000 }).toContain('Page 2 / 3');
});

test('picking an entry fires its action', async ({ player }) => {
  player.chat('/vpaged');
  const gui = await player.gui({ title: /Entries/ });
  await gui.locator((i) => i.displayName.includes('Entry #3')).click();
  await expect(player).toHaveReceivedMessage('Picked Entry #3');
});
