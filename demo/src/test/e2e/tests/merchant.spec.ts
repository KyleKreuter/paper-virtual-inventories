import { expect, test } from '@plugwright/runner';

test('merchant sends two compiled offers', async ({ player }) => {
  const client = (player.bot as any)._client;
  const offersPromise = new Promise<any>((resolve) => {
    client.once('trade_list', (packet: any) => resolve(packet));
  });
  player.chat('/vmenu merchant');
  const gui = await player.gui({ title: /Trader/ });
  expect(gui.title).toMatch(/Trader/);
  const packet = await offersPromise;
  expect(packet.trades?.length ?? packet.offers?.length).toBe(2);
});
