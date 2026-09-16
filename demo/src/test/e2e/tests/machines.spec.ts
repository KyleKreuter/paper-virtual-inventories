import { expect, test } from '@plugwright/runner';

const CASES: Array<[string, RegExp]> = [
  ['furnace', /Smelter/],
  ['smoker', /Smoker/],
  ['brewing', /Brewery/],
  ['enchant', /Enchanter/],
  ['stonecutter', /Stonecutter/],
  ['loom', /Loom/],
];

for (const [id, title] of CASES) {
  test(`vmenu ${id} opens with the right window type and title`, async ({ player }) => {
    player.chat(`/vmenu ${id}`);
    const gui = await player.gui({ title });
    expect(gui.title).toMatch(title);
  });
}
