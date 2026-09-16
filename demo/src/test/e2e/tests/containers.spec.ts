import { expect, test } from '@plugwright/runner';

const cases = [
  ['hopper', 'Hopper'],
  ['shulker', 'Shulker'],
  ['dispenser', 'Dispenser'],
  ['crafter', 'Crafter'],
] as const;

for (const [id, word] of cases) {
  test(`${id} opens the ${word} window`, async ({ player }) => {
    player.chat(`/vmenu ${id}`);
    const gui = await player.gui({ title: new RegExp(word) });
    expect(gui.title).toContain(word);
  });
}
