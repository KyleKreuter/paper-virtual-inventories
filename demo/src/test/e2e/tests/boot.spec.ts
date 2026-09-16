import { expect, test } from '@plugwright/runner';

test('vreload recompiles and loads all menus', async ({ player, server }) => {
  // The runner attaches to the server log after boot, so boot-time lines are
  // missed. Trigger a deterministic recompile instead (console can't run
  // /vreload — it's players-only).
  await player.chat('/vreload');
  for (const id of ['demo', 'paged1', 'paged2', 'paged3', 'name']) {
    await expect(server).toHaveReceivedMessage(`Loaded compiled menu '${id}'`);
  }
});
