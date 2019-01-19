package com.github.euonmyoji.epicbanitem.command.arg;

import com.github.euonmyoji.epicbanitem.EpicBanItem;
import com.github.euonmyoji.epicbanitem.check.CheckRule;
import com.github.euonmyoji.epicbanitem.check.CheckRuleIndex;
import com.github.euonmyoji.epicbanitem.check.CheckRuleService;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.ArgumentParseException;
import org.spongepowered.api.command.args.CommandArgs;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.CommandElement;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.StartsWithPredicate;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author yinyangshi GiNYAi ustc_zzzz
 */
@NonnullByDefault
class ArgItemCheckRule extends CommandElement {

    ArgItemCheckRule(@Nullable Text key) {
        super(key);
    }

    @Override
    public void parse(CommandSource source, CommandArgs args, CommandContext context) throws ArgumentParseException {
        ItemType itemType = context.<ItemType>getOne("item-type").orElseThrow(NoSuchFieldError::new);
        CheckRuleIndex index = CheckRuleIndex.of(itemType);
        String argString = args.next();
        CheckRuleService service = Sponge.getServiceManager().provideUnchecked(CheckRuleService.class);
        Optional<CheckRule> optionalCheckRule = service.getCheckRuleByNameAndIndex(index, argString);
        if (optionalCheckRule.isPresent()) {
            context.putArg(getKey(), optionalCheckRule.get());
        } else {
            throw args.createError(EpicBanItem.getMessages()
                    .getMessage("epicbanitem.args.itemCheckRule.notFound", "name", argString, "item", itemType.getId()));
        }
    }

    @Override
    protected Object parseValue(CommandSource source, CommandArgs args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> complete(CommandSource src, CommandArgs args, CommandContext context) {
        ItemType itemType = context.<ItemType>getOne("item-type").orElseThrow(NoSuchFieldError::new);
        CheckRuleIndex index = CheckRuleIndex.of(itemType);
        String prefix = args.nextIfPresent().orElse("").toLowerCase();
        CheckRuleService service = Sponge.getServiceManager().provideUnchecked(CheckRuleService.class);
        return service.getCheckRulesByIndex(index).stream().map(CheckRule::getName).filter(new StartsWithPredicate(prefix)).collect(Collectors.toList());
    }
}