package com.github.Jikoo.CustomMines;

import java.util.Random;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class CustomMines extends JavaPlugin implements Listener{
	FileConfiguration config;
	static Random r = new Random();
	@Override
	public void onEnable() {
		saveDefaultConfig();
		config = getConfig();
		getServer().getPluginManager().registerEvents(this, this);
		getLogger().info("CustomMines enabled!");
	}
	
	@Override
	public void onDisable() {
		getLogger().info("CustomMines disabled!");
	}
	
	
	public static int getVanillaExp(Material m){
		if(m.equals(Material.COAL_ORE)){
			return r.nextInt(3);//0-2
		} else if (m.equals(Material.REDSTONE_ORE)){
			return 1+r.nextInt(5);//3-7
		} else if (m.equals(Material.DIAMOND_ORE)||m.equals(Material.EMERALD_ORE)){
			return 3+r.nextInt(5);//3-7
		} else if (m.equals(Material.LAPIS_ORE)/*||m.equals(Material.NETHERQUARTZ_ORE)*/){
			return 2+r.nextInt(4);//2-5 TODO on 1.5 release un-comment nether quartz (if that's even the right Material name)
		} else if (m.equals(Material.MOB_SPAWNER)){
			return 15+r.nextInt(29);//15-43
		}
		
		
		
		
		return 0;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		if(command.getName().equalsIgnoreCase("custommines")){
			if(sender instanceof Player&&(!sender.hasPermission("miner.command")||(!config.getBoolean("permissions")&&!sender.isOp()))){
				if(config.contains("denied-command"))
					sender.sendMessage(config.getString("denied-command"));
			}
			if(args.length>=1){
				if(args[0].equalsIgnoreCase("reload")){
					saveDefaultConfig();
					config = getConfig();
					sender.sendMessage("Config reloaded!");
					return true;
				}
			}
			sender.sendMessage(ChatColor.DARK_AQUA+"CustomMines is enabled!");
			sender.sendMessage(ChatColor.DARK_AQUA+"Use \""+ChatColor.DARK_GREEN+"/cmines reload"+ChatColor.DARK_AQUA+"\" to reload the config.");
		}
		return true;
	}
	
	
	
	
	@EventHandler
	public void OnBlockBreak(BlockBreakEvent event){
		if(!event.isCancelled()&&!(event.getPlayer().getGameMode().equals(GameMode.CREATIVE))){
			Set<String> changedBlocks = config.getConfigurationSection("blocks").getKeys(false);
			Block b = event.getBlock();
			String blockNode="";
			for(String s : changedBlocks){
				if(b.getType().equals(Material.matchMaterial(s))||s.equals(b.getTypeId()+"")){
					blockNode = s.toString();
					break;
				}
			}
			if(blockNode.equals("")){
				return;
			} else {
				Player p = event.getPlayer();
				ItemStack tool = p.getItemInHand();
				if(!config.getBoolean("permissions")||!config.getBoolean("per-block-perms")||p.hasPermission("miner.allblocks")||p.hasPermission("miner."+blockNode)){
					if(p.hasPermission("miner.anytool")||(event.getBlock().getDrops(tool)!=null)||p.hasPermission("miner.silkall")){
						//explosion
						if(!(p.hasPermission("miner.noexplode")||p.hasPermission("miner."+blockNode+".noexplode")||(!config.getBoolean("permissions")&&p.isOp()))){
							if(config.getDouble("blocks."+blockNode+".explode.percent")>((double) r.nextInt(100000)/1000)){
								b.getWorld().createExplosion(b.getLocation(), (float) config.getInt("blocks."+blockNode+".explode.power"));
								if(!p.hasPermission("miner.nodamage")||(!config.getBoolean("permissions")&&p.isOp()))
									tool.setDurability((short) (tool.getDurability()+config.getInt("blocks."+blockNode+".explode.toolDamage")));
							}
						}
						
						//damage tool for mining
						if(!p.hasPermission("miner.nodamage")||(!config.getBoolean("permissions")&&p.isOp())){
							tool.setDurability((short) (tool.getDurability()+1));
						}
						
						//silk touch
						if(tool.getEnchantmentLevel(Enchantment.SILK_TOUCH)>0){
							if(event.getBlock().getDrops(tool)!=null||p.hasPermission("miner.silkall")||(!config.getBoolean("permissions")&&p.isOp())){
								b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(b.getTypeId(), 1));
							}
							b.setType(Material.AIR);
							event.setCancelled(true);
							return;
						}
						//handling drops
						Set<String> drops = config.getConfigurationSection("blocks."+blockNode+".drops").getKeys(false);
						for(String itemType : drops){
							if(!itemType.equals("exp")){
								Material m = Material.matchMaterial(itemType);
								if(m!=null){
									if(!config.getBoolean("permissions")||!config.getBoolean("per-drop-perms")||p.hasPermission("miner.alldrops")||p.hasPermission("miner."+blockNode+"."+itemType)){
										int max = config.getInt("blocks."+blockNode+".drops."+itemType+".max");
										int min = config.getInt("blocks."+blockNode+".drops."+itemType+".min");
										max-=(min-1);
										int numDrops;
										if(max>1)
											numDrops = min+ r.nextInt(max);
										else numDrops = min;
										
										//fortune style - 1 = vanilla, chance per item to add 0 to [fortune level] drops
										int finalDrops = numDrops+r.nextInt(tool.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS)+1);
										int fortune = config.getInt("blocks."+blockNode+".drops."+itemType+".fortune-type");
										if(fortune==1){
											for(int i=0;i<numDrops;i++){
												finalDrops+=1+r.nextInt(tool.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS)+1);
											}
										} else if(fortune==0){
											finalDrops = numDrops;
										}
										
										//do the dropping
										if(config.getBoolean("drop-spray")){
											for(int i=0;i<finalDrops;i++){
												b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(m, 1));
											}
										} else {
											b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(m, finalDrops));
										}
									}
								} else {
									getLogger().warning(ChatColor.DARK_RED+"\""+ itemType + "\" is not a valid Material! Please fix your config.");
								}
							} else {
								//handle exp dropping
								if(config.getInt("blocks."+blockNode+".drops.exp.type")==2){
									int minExp = config.getInt("blocks."+blockNode+".drops.exp.min");
									int maxExp = config.getInt("blocks."+blockNode+".drops.exp.max");
									if(minExp>0||maxExp>0){
										if(minExp >= maxExp){
											((ExperienceOrb)b.getWorld().spawn(b.getLocation(), ExperienceOrb.class)).setExperience(minExp);
										} else {
											((ExperienceOrb)b.getWorld().spawn(b.getLocation(), ExperienceOrb.class)).setExperience(minExp+r.nextInt(1+maxExp-minExp));
										}
									}
								} else {
									int expDropped = getVanillaExp(b.getType());
									if(expDropped>0)
										((ExperienceOrb)b.getWorld().spawn(b.getLocation(), ExperienceOrb.class)).setExperience(expDropped);
								}
							} 
						}
						if(!drops.contains("exp")) {
							//if absent, assume vanilla
							int expDropped = getVanillaExp(b.getType());
							if (expDropped>0)
								((ExperienceOrb)b.getWorld().spawn(b.getLocation(), ExperienceOrb.class)).setExperience(expDropped);
						}
						event.setCancelled(true);
						b.setType(Material.AIR);
						p.updateInventory();
					}
				}
			}
		}
	}
}
