package com.github.euonmyoji.epicbanitem.command;

import com.github.euonmyoji.epicbanitem.EpicBanItem;
import com.github.euonmyoji.epicbanitem.util.TextUtil;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.*;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author EBI TEAM
 */
@SuppressWarnings("WeakerAccess")
public abstract class AbstractCommand implements ICommand, CommandExecutor {

    protected CommandSpec commandSpec;

    protected Help help;

    protected String name;

    protected String[] alias;

    protected String parent = "";

    public AbstractCommand(String name, String... alias) {
        this.name = name;
        this.alias = alias;
    }

    private void init() {
        if (commandSpec == null) {
            help = new Help();
            commandSpec = CommandSpec.builder()
                    .permission(getPermission("base"))
                    .description(getDescription())
                    .extendedDescription(getExtendedDescription())
                    .arguments(help)
                    .executor(help)
                    .build();
        }
    }

    public String getCommandString() {
        return "/" + parent + name + " ";
    }

    public String getRootPermission() {
        return "epicbanitem.command." + name;
    }

    protected String getPermission(String s) {
        return getRootPermission() + "." + s;
    }

    protected String getMessageKey(String s) {
        return "epicbanitem.command." + name + "." + s;
    }

    protected Text getMessage(String s) {
        return EpicBanItem.plugin.getMessages().getMessage(getMessageKey(s));
    }

    protected Text getMessage(String s, String k1, Object v1) {
        return EpicBanItem.plugin.getMessages().getMessage(getMessageKey(s), k1, v1);
    }

    public Text getDescription() {
        return getMessage("description");
    }

    public Text getExtendedDescription() {
        return getMessage("extendedDescription");
    }

    public Text getArgHelp(CommandSource source) {
        init();
        CommandElement element = help.element;
        if (element.equals(GenericArguments.none())) {
            return Text.EMPTY;
        }
        Text.Builder builder = Text.builder();
        builder.append(EpicBanItem.plugin.getMessages().getMessage("epicbanitem.commands.args"));
        if (element instanceof CommandFlags) {
            try {
                Field field = CommandFlags.class.getDeclaredField("usageFlags");
                field.setAccessible(true);
                Map<?, ?> usageFlags = (Map<?, ?>) field.get(element);
                for (Map.Entry<?, ?> entry : usageFlags.entrySet()) {
                    List<?> availableFlags = (List<?>) entry.getKey();
                    CommandElement childElement = (CommandElement) entry.getValue();
                    List<Object> objects = new ArrayList<>();
                    objects.add("[");
                    Iterator<?> it = availableFlags.iterator();
                    while (it.hasNext()) {
                        String flag = (String) it.next();
                        objects.add(flag.length() > 1 ? "--" : "-");
                        objects.add(flag);
                        if (it.hasNext()) {
                            objects.add("|");
                        }
                    }
                    Text usage = childElement.getUsage(source);
                    if (usage.toPlain().trim().length() > 0) {
                        objects.add(" ");
                        objects.add(usage);
                    }
                    objects.add("]");
                    objects.add(" ");
                    String id = availableFlags.get(0).toString();
                    builder.append(Text.NEW_LINE,
                            Text.of("    "), TextUtil.adjustLength(Text.of(objects.toArray()), 30),
                            getMessage("flags." + id));
                }
                Field field1 = CommandFlags.class.getDeclaredField("childElement");
                field1.setAccessible(true);
                element = (CommandElement) field1.get(element);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                EpicBanItem.logger.error("Failed to parse help for CommandFlags");
            }
        }
        if (element != null) {
            scanArg(element, source, builder);
        }
        return builder.toText();
    }

    private void scanArg(CommandElement commandElement, CommandSource source, Text.Builder builder) {
        // Need Permission Check?
        String id = commandElement.getUntranslatedKey();
        if (id == null) {
            Class<? extends CommandElement> clazz = commandElement.getClass();
            try {
                Field field = clazz.getDeclaredField("elements");
                field.setAccessible(true);
                Object elements = field.get(commandElement);
                if (elements instanceof List) {
                    for (Object element : (List) elements) {
                        if (element instanceof CommandElement) {
                            scanArg((CommandElement) element, source, builder);
                        }
                    }
                    return;
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                //do nothing
            }
            try {
                Field field = clazz.getDeclaredField("element");
                field.setAccessible(true);
                Object element = field.get(commandElement);
                if (element instanceof CommandElement) {
                    scanArg((CommandElement) element, source, builder);
                }
            } catch (IllegalAccessException | NoSuchFieldException e) {
                //do nothing
            }
        } else {
            builder.append(Text.NEW_LINE,
                    Text.of("    "), TextUtil.adjustLength(commandElement.getUsage(source), 30),
                    getMessage("args." + id));
        }

    }

    public Text getHelpMessage(CommandSource src, CommandContext args) {
        init();
        Text.Builder builder = Text.builder();
        builder.append(EpicBanItem.plugin.getMessages().getMessage("epicbanitem.commands.name",
                "name", getName(),
                "alias", String.join(" ", getAlias())), Text.NEW_LINE);
        builder.append(getDescription(), Text.NEW_LINE);
        builder.append(EpicBanItem.plugin.getMessages().getMessage("epicbanitem.commands.usage", "usage", Text.of(getCommandString(), getCallable().getUsage(src))), Text.NEW_LINE);
        builder.append(getArgHelp(src), Text.NEW_LINE);
//                builder.append(getExtendedDescription(),Text.NEW_LINE);
        return builder.build();
    }

    public abstract CommandElement getArgument();

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String[] getAlias() {
        return alias;
    }

    @Override
    public CommandSpec getCallable() {
        init();
        return commandSpec;
    }

    @NonnullByDefault
    private class Help extends CommandElement implements CommandExecutor {
        private CommandElement element = getArgument();

        private Help() {
            super(Text.of("help"));
        }

        @Override
        public void parse(CommandSource source, CommandArgs args, CommandContext context) throws ArgumentParseException {
            try {
                element.parse(source, args, context);
                if (args.hasNext()) {
                    //avoid too many arguments
                    context.putArg("help", true);
                    while (args.hasNext()) {
                        args.next();
                    }
                }
            } catch (ArgumentParseException e) {
                context.putArg("help", true);
                while (args.hasNext()) {
                    args.next();
                }
            }
        }

        @Nullable
        @Override
        protected Object parseValue(CommandSource source, CommandArgs args) {
            //do nothing here
            return null;
        }

        @Override
        public List<String> complete(CommandSource src, CommandArgs args, CommandContext context) {
            try {
                if (!args.hasNext() || "help".startsWith(args.peek().toLowerCase())) {
                    List<String> stringList = new ArrayList<>();
                    stringList.add("help");
                    stringList.addAll(element.complete(src, args, context));
                    return stringList;
                } else {
                    return element.complete(src, args, context);
                }
            } catch (ArgumentParseException e) {
                e.printStackTrace();
                return element.complete(src, args, context);
            }
        }

        @Override
        public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
            if (args.hasAny("help")) {
                src.sendMessage(getHelpMessage(src, args));
                return CommandResult.success();
            } else {
                return AbstractCommand.this.execute(src, args);
            }
        }

        @Override
        public Text getUsage(CommandSource src) {
            Text usage = element.getUsage(src);
            if (usage.isEmpty()) {
                return Text.of("[help]");
            } else {
                return Text.of(usage, "|help");
            }
        }
    }

}
