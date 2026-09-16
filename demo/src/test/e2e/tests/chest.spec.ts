import { expect, test } from '@plugwright/runner';

test('vmenu opens the compiled chest menu', async ({ player }) => {
  player.chat('/vmenu');
  const gui = await player.gui({ title: /Packet Chest/ });
  expect(gui.title).toContain('Packet Chest');
  await expect(player).toHaveReceivedMessage('Welcome to the compiled demo menu.');
});

test('clicking the diamond fires the action and dupes nothing', async ({ player }) => {
  player.chat('/vmenu');
  const gui = await player.gui({ title: /Packet Chest/ });
  await gui.locator((i) => i.displayName.includes('Click me')).click();
  await expect(player).toHaveReceivedMessage('Clicked slot 13');
  await expect(player).not.toContainItem('diamond');
});

test('dynamic lore refreshes live after bump', async ({ player }) => {
  player.chat('/vmenu');
  const gui = await player.gui({ title: /Packet Chest/ });
  const stats = gui.locator((i) => i.displayName.includes('Stats'));
  await expect(stats).toHaveLore('Balance: 0');
  await gui.locator((i) => i.displayName.includes('+1 Level')).click();
  await expect(player).toHaveReceivedMessage('Balance bumped');
  await expect(stats).toHaveLore('Balance: 137');
});
