package com.github.sevencnanashi.cursorxp;

import com.google.gson.JsonParseException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class CursorXpClient implements ClientModInitializer {
  private static final Pattern MP_COST =
      Pattern.compile("([0-9]+)\\s+消費MP", Pattern.UNICODE_CHARACTER_CLASS);

  @Override
  public void onInitializeClient() {
    HudRenderCallback.EVENT.register(
        (context, tickDelta) -> {
          MinecraftClient client = MinecraftClient.getInstance();
          if (client.player == null
              || client.options.hudHidden
              || !client.options.getPerspective().isFirstPerson()
              || client.player.isSpectator()) {
            return;
          }
          ItemStack mainHand = client.player.getMainHandStack();
          ItemStack spellBook;
          switch (mainHand.getName().getString()) {
            case "魔法の杖" -> spellBook = client.player.getOffHandStack();
            case "火の魔導書", "氷の魔導書", "雷の魔導書" -> spellBook = mainHand;
            default -> {
              return;
            }
          }

          String level = Integer.toString(Math.min(client.player.experienceLevel, 100));
          int x = (context.getScaledWindowWidth() - client.textRenderer.getWidth(level)) / 2;
          int y = context.getScaledWindowHeight() / 2 + 12;
          int color =
              switch (spellBook.getName().getString()) {
                case "火の魔導書" -> 0xFF5555;
                case "氷の魔導書" -> 0x5555FF;
                case "雷の魔導書" -> 0xFFFF55;
                default -> 0x80FF20;
              };
          int alpha =
              client.player.getAttackCooldownProgress(tickDelta) < 1.0F ? 0x80000000 : 0xFF000000;
          int levelColor = mainHand.getName().getString().equals("魔法の杖") ? 0xFFFFFF : color;
          context.drawTextWithShadow(client.textRenderer, level, x, y, alpha | levelColor);

          List<BigInteger> costs = getMpCosts(spellBook);
          BigInteger availableMp = BigInteger.valueOf(client.player.experienceLevel);
          int dotWidth = client.textRenderer.getWidth("●");
          int spacing = dotWidth + 3;
          int startX = (context.getScaledWindowWidth() - (costs.size() * spacing - 3)) / 2;
          for (int i = 0; i < costs.size(); i++) {
            int dotColor = availableMp.compareTo(costs.get(i)) >= 0 ? color : 0x808080;
            context.drawTextWithShadow(
                client.textRenderer,
                "●",
                startX + i * spacing,
                y + client.textRenderer.fontHeight + 2,
                alpha | dotColor);
          }
        });
  }

  private static List<BigInteger> getMpCosts(ItemStack stack) {
    NbtCompound display = stack.getSubNbt("display");
    if (display == null) {
      return List.of();
    }
    NbtList lore = display.getList("Lore", NbtElement.STRING_TYPE);
    List<BigInteger> costs = new ArrayList<>();
    for (int i = 0; i < lore.size(); i++) {
      Text line;
      try {
        line = Text.Serialization.fromJson(lore.getString(i));
      } catch (JsonParseException e) {
        // 不正な説明文でHUD全体の描画を止めない。
        continue;
      }
      if (line == null) {
        continue;
      }
      Matcher matcher = MP_COST.matcher(Formatting.strip(line.getString()));
      while (matcher.find()) {
        costs.add(new BigInteger(matcher.group(1)));
      }
    }
    return costs;
  }
}
