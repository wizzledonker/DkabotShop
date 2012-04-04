package com.dkabot.DkabotShop;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.avaje.ebean.PagingList;

public class History implements CommandExecutor {
	private DkabotShop plugin;
	public History(DkabotShop plugin) {
		this.plugin = plugin;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(!(sender instanceof Player)) {
			sender.sendMessage(ChatColor.RED + "Sorry, console command usage is not yet supported!");
			return true;
		}
		if(cmd.getName().equalsIgnoreCase("sales")) {
			if(!sender.hasPermission("dkabotshop.sales")) {
				sender.sendMessage(ChatColor.RED + "You lack permission to do this.");
				return true;
			}
			if(args.length > 2) {
				sender.sendMessage(ChatColor.RED + "Too many arguments.");
				return true;
			}
			//Declare Variables
			Material material = null;
			Integer page = 0;
			List<DB_History> DBClass = null;
			PagingList<DB_History> DBPageList = null;
			Integer hyphenCount;
			String hyphens = "";
			if(args.length == 0) {
				DBClass = plugin.getDatabase().find(DB_History.class).where().orderBy().desc("id").setMaxRows(8).findList();
				if(DBClass.isEmpty()) {
					sender.sendMessage(ChatColor.RED + "Nobody has sold anything yet!");
					return true;
				}
			}
			
			//1 argument, could be page number or item
			if(args.length == 1) {
				//try for it as a material
				material = plugin.getMaterial(args[0], true, (Player) sender);
				if(material != null) {
					//Nice try, still blacklisted
					if(plugin.illegalItem(material) == 1) {
						sender.sendMessage(ChatColor.RED + "Disallowed item!");
						return true;
					}
				}
				//try for it as a page
				else {
					//Don't want errors
					if(!plugin.isInt(args[0])) {
						sender.sendMessage(ChatColor.RED + "Page number must be an integer.");
						return true;
					}
					page = Integer.parseInt(args[0]) - 1;
				}
			}
			
			//2 arguments, both a page number and item
			if(args.length == 2) {
				//try the material
				material = plugin.getMaterial(args[0], true, (Player) sender);
				//this is not the material you are looking for
				if(material == null) {
					sender.sendMessage(ChatColor.RED + "Invalid item!");
					return true;
				}
				//nice try, still blacklisted
				if(plugin.illegalItem(material) == 1) {
					sender.sendMessage(ChatColor.RED + "Disallowed item!");
					return true;
				}
				//try the page number
				//don't want errors
				if(!plugin.isInt(args[1])) {
					sender.sendMessage(ChatColor.RED + "Page number must be an Integer.");
					return true;
				}
				page = Integer.parseInt(args[1]) - 1;
			}
			if(page < 0) {
				sender.sendMessage(ChatColor.RED + "Invalid page number!");
				return true;
			}
			//get results with the item and page (default 1)
			if(material != null) {
				DBPageList = plugin.getDatabase().find(DB_History.class).where().eq("item", material.toString()).orderBy().desc("id").findPagingList(8);
				DBClass = DBPageList.getPage(page).getList();
				if(DBClass.isEmpty()) {
					if(page == 0) sender.sendMessage(ChatColor.RED + "Nobody has sold any " + material.toString() + " yet.");
					else sender.sendMessage(ChatColor.RED + "Page " + page + " contains no results. Try page 1.");
					return true;
				}
				//send the confirmation note now, for convenience
				sender.sendMessage(ChatColor.GREEN + "Last Transactions, " + material.toString() + " Only:");
			}
			//get results without the item but with page (default 1)
			else {
				DBPageList = plugin.getDatabase().find(DB_History.class).where().orderBy().desc("id").findPagingList(8);
				DBClass = DBPageList.getPage(page).getList();
				if(DBClass.isEmpty()) {
					if(page == 0) sender.sendMessage(ChatColor.RED + "Nobody has sold anything yet.");
					else sender.sendMessage(ChatColor.RED + "Page " + page + " contains no results. Try page 1.");
					return true;
				}
				//send the confirmation note, since we won't later
				sender.sendMessage(ChatColor.GREEN + "Last Transactions, No Filter:");
			}
			//Page number validation
			//hyphen builder
			hyphenCount = ((40 - (" Page " + (page + 1) + " ").length()) / 2);
			for(Integer i = 0; i < hyphenCount;) {
				hyphens = hyphens + "-";
				i++;
			}
			//send that info!
			sender.sendMessage(ChatColor.RED + hyphens + ChatColor.GRAY + " Page " + ChatColor.RED + (page + 1) + " " + hyphens);
			for(Integer i = 0; i < DBClass.size();) {
				DB_History DB = DBClass.get(i);
				String currencyName = "Error Getting Currency";
				if(DB.getCost() == 1) currencyName = plugin.economy.currencyNameSingular();
				else currencyName = plugin.economy.currencyNamePlural();
				sender.sendMessage(ChatColor.GOLD + DB.getBuyer() + ChatColor.BLUE + " bought " + ChatColor.GOLD +  DB.getAmount() + " " + DB.getItem() + ChatColor.BLUE + " for about " + ChatColor.GOLD + DB.getCost() + " " + currencyName + ChatColor.BLUE + " each.");
				i++;
			}
			if(DBPageList.getPage(page).hasNext()) sender.sendMessage(ChatColor.GREEN + "There is a next page in this list!");
			return true;
		}
		//If you made it to here, show me the Herobrine that helped you do it!
		return false;
	}

}
