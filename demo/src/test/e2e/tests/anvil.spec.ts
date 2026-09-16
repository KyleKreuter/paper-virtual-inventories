import { expect, test } from '@plugwright/runner';

test('rename via anvil text field reaches the confirm action', async ({ player }) => {
  player.chat('/vname');
  const gui = await player.gui({ title: /Name it/ });
  // Raw rename packet, like a vanilla client sends while typing.
  (player.bot as any)._client.write('name_item', { name: 'SirTestalot' });
  await gui.locator((i) => i.displayName.includes('Confirm')).click();
  await expect(player).toHaveReceivedMessage('Confirmed name: SirTestalot');
});

test('confirm without typing reports empty', async ({ player }) => {
  player.chat('/vname');
  const gui = await player.gui({ title: /Name it/ });
  await gui.locator((i) => i.displayName.includes('Confirm')).click();
  await expect(player).toHaveReceivedMessage('Confirmed name: (empty)');
});
