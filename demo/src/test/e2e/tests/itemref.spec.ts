import { expect, test } from '@plugwright/runner';

test('ref slot renders the seeded crown with full NBT', async ({ player }) => {
  player.chat('/vmenu');
  const gui = await player.gui({ title: /Packet Chest/ });
  const crown = gui.locator((i) => i.displayName.includes('Demo Crown'));
  await expect(crown).toHaveLore('Forged for demo purposes');
  await expect(crown).toHaveLore('Ref: demo_crown');
});

test('/vitem give hands over the stored stack', async ({ player }) => {
  player.chat('/vitem give demo_crown');
  await expect(player).toHaveReceivedMessage("Given item 'demo_crown'.");
  await expect(player).toContainItem('diamond_helmet');
});

test('/vitem rejects unknown ids loudly', async ({ player }) => {
  player.chat('/vitem give nope_missing');
  await expect(player).toHaveReceivedMessage("Unknown item 'nope_missing'.");
});
