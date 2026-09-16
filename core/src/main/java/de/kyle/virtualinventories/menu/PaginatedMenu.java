package de.kyle.virtualinventories.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * A virtual menu with paginated content. The last row is reserved for navigation
 * controls, all rows above show page content.
 */
public abstract class PaginatedMenu extends VirtualMenu {

    private final List<MenuItem> content = new ArrayList<>();
    private int page;

    protected PaginatedMenu(Component title, MenuSize size) {
        super(title, size);
        if (size.rows() < 2) {
            throw new IllegalArgumentException("PaginatedMenu needs at least 2 rows");
        }
    }

    protected int contentSlots() {
        return size.slots() - 9;
    }

    public int page() {
        return page;
    }

    public int pageCount() {
        return Math.max(1, (int) Math.ceil(content.size() / (double) contentSlots()));
    }

    public void setContent(List<MenuItem> items) {
        content.clear();
        content.addAll(items);
        page = 0;
        renderPage();
    }

    public void addContent(MenuItem item) {
        content.add(item);
        renderPage();
    }

    public void nextPage() {
        if (page + 1 < pageCount()) {
            page++;
            renderPage();
        }
    }

    public void previousPage() {
        if (page > 0) {
            page--;
            renderPage();
        }
    }

    /** Rebuilds visible slots from the current page. Call {@code session.refresh()} after. */
    public void renderPage() {
        int perPage = contentSlots();
        int start = page * perPage;
        for (int i = 0; i < perPage; i++) {
            int index = start + i;
            items[i] = index < content.size() ? content.get(index) : null;
        }
        int base = size.slots() - 9;
        items[base] = page > 0
                ? MenuItem.of(named(Material.ARROW, "Previous page"), ctx -> {
                    previousPage();
                    ctx.session().refresh();
                })
                : MenuItem.just(named(Material.GRAY_STAINED_GLASS_PANE, " "));
        items[base + 4] = MenuItem.just(named(Material.PAPER, "Page " + (page + 1) + " / " + pageCount()));
        items[base + 8] = page + 1 < pageCount()
                ? MenuItem.of(named(Material.ARROW, "Next page"), ctx -> {
                    nextPage();
                    ctx.session().refresh();
                })
                : MenuItem.just(named(Material.GRAY_STAINED_GLASS_PANE, " "));
        for (int i = base + 1; i < base + 8; i++) {
            if (i != base + 4 && items[i] == null) {
                items[i] = MenuItem.just(named(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }
    }

    private static ItemStack named(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
