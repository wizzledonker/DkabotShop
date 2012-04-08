package com.dkabot.DkabotShop;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.avaje.ebean.PagingList;

public class Buyers implements CommandExecutor {
	private DkabotShop plugin;
	public Buyers(DkabotShop plugin) {
		this.plugin = plugin;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(!(sender instanceof Player)) {
			sender.sendMessage(ChatColor.RED + "Sorry, console command usage is not yet supported!");
			return true;
		}
		//Code for /buy
		if(cmd.getName().equalsIgnoreCase("buy")) {
			//Player check
			if (!(sender instanceof Player)) {
				sender.sendMessage(ChatColor.RED + "Only players can buy items!");
				return true;
			}
			//Permission Check
			if(!sender.hasPermission("dkabotshop.buy")) {
				sender.sendMessage(ChatColor.RED + "You lack permission to do this.");
				return true;
			}
			//Argument length validation
			if(args.length < 2) {
				sender.sendMessage(ChatColor.RED + "Too few arguments.");
				return true;
			}
			if(args.length > 3) {
				sender.sendMessage(ChatColor.RED + "Too many arguments.");
				return true;
			}
			//Declare variables
			Material material;
			Integer amount = null;
			Integer lastSellerAmount = null;
			Integer amountRemaining = null;
			Integer prevAmountRemaining = null;
			Double totalCost = 0.00;
			Double maxPrice = null;
			Integer amountGiven = 0;
			Integer sellers;
			Player player = (Player) sender;
			String currencyName = "Error Getting Currency";
			//Item validation
			material = plugin.getMaterial(args[0], true, player);
			if(material == null) {
				sender.sendMessage(ChatColor.RED + "Invalid item!");
				return true;
			}
			//Nice try, but still no
			if(plugin.illegalItem(material) == 1) {
				sender.sendMessage(ChatColor.RED + "Disallowed item!");
				return true;
			}
			//Amount setting and validation
			if(!plugin.isInt(args[1])) {
				sender.sendMessage(ChatColor.RED + "Amount to buy must be a number.");
				return true;
			}
			amount = Integer.parseInt(args[1]);
			amountRemaining = amount;
			if(amount <= 0) {
				sender.sendMessage(ChatColor.RED + "Amount to buy cannot be 0 or negative.");
				return true;
			}
			//Set max cost if specified
			if(args.length == 3) {
				maxPrice = plugin.getMoney(args[2]);
				//Validate max cost
				if(maxPrice == null) {
					sender.sendMessage(ChatColor.RED + "Invalid maximum purchase price!");
					return true;
				}
				if(maxPrice <= 0) {
					sender.sendMessage(ChatColor.RED + "Max purchase price cannot be 0 or negative!");
					return true;
				}
			}
			//Get all instances of this item for sale, save for the ones sold by the buyer
			List<DB_ForSale> DBClass = plugin.getDatabase().find(DB_ForSale.class).where().eq("item", material.toString()).ne("seller", sender.getName()).orderBy().asc("cost").findList();
			//Loop through all the entries
			for(sellers = 0; sellers < DBClass.size();) {
				//Get the specific entry
				DB_ForSale tmpDB = DBClass.get(sellers);
				//Max price check, if applicable
				if(maxPrice != null && tmpDB.getCost() > maxPrice) break;
				//Previous amount remaining to be bought, for calculations
				prevAmountRemaining = amountRemaining;
				//Set amount remaining
				amountRemaining = amountRemaining - tmpDB.getAmount();
				//If the amount to buy has been reached
				if(amountRemaining <= 0) {
					//Set new amount in the shop
					lastSellerAmount = prevAmountRemaining;
					//Add to the total cost for the buyer
					totalCost = totalCost + prevAmountRemaining * tmpDB.getCost();
					break;
				}
				else {
					//Add to the total cost for the buyer
					totalCost = totalCost + tmpDB.getAmount() * tmpDB.getCost();
				}
				//Continue through the loop
				sellers++;
			}
			//Check if not even one item fit the criteria
			if(amountRemaining == amount) {
				sender.sendMessage(ChatColor.RED + "Nobody is selling this!");
				return true;
			}
			//Check if not enough items fit the criteria
			if(amountRemaining > 0) {
				sender.sendMessage(ChatColor.RED + "There isn't enough for sale!");
				return true;
			}
			//Check if the buyer has enough funds
			if(plugin.economy.getBalance(sender.getName()) < totalCost) {
				sender.sendMessage(ChatColor.RED + "You lack enough funds, you need " + ChatColor.YELLOW + (totalCost - plugin.economy.getBalance(sender.getName())) + ChatColor.RED + " more Sky Coins!");
				return true;
			}
			//Finally, the transaction actually takes place!
			//Give the player items
			for(Integer i = 0; i < sellers + 1;) {
				if(i < sellers) {
					DB_ForSale tmpDB = DBClass.get(i);
					amountGiven = amountGiven + tmpDB.getAmount();
					Integer amountNotReturned = plugin.giveItem(new ItemStack(material, tmpDB.getAmount()), player);
					if(amountNotReturned != 0) {
						sender.sendMessage(ChatColor.RED + "You lack enough space for this!");
						player.getInventory().removeItem(new ItemStack(material, amountGiven - amountNotReturned));
						return true;
					}
				}
				else {
					amountGiven = amountGiven + lastSellerAmount;
					Integer amountNotReturned = plugin.giveItem(new ItemStack(material, lastSellerAmount), player);
					if(amountNotReturned != 0) {
						sender.sendMessage(ChatColor.RED + "You lack enough space for this!");
						player.getInventory().removeItem(new ItemStack(material, amountGiven - amountNotReturned));
						return true;
					}
				}
				i++;
			}
			//Take funds from the player
			plugin.economy.withdrawPlayer(sender.getName(), totalCost);
			for(Integer i = 0; i < sellers;) {
				DB_ForSale tmpDB = DBClass.get(i);
				//For any and all sellers sold out, give them money and remove their DB entry
				plugin.economy.depositPlayer(tmpDB.getSeller(), tmpDB.getAmount() * tmpDB.getCost());
				Player seller = Bukkit.getServer().getPlayer(tmpDB.getSeller());
				if(seller != null) seller.sendMessage(ChatColor.GOLD + sender.getName() + ChatColor.BLUE + " bought all of your shop's " + ChatColor.GOLD + material.toString() + ChatColor.BLUE + "!");
				plugin.getDatabase().delete(tmpDB);
				i++;
			}
			//Set shop amount for final seller and give them money
				DB_ForSale finalSellerDB = DBClass.get(sellers);
				if(finalSellerDB.getAmount() - lastSellerAmount <= 0) plugin.getDatabase().delete(finalSellerDB);
				else finalSellerDB.setAmount(finalSellerDB.getAmount() - lastSellerAmount);
				plugin.economy.depositPlayer(finalSellerDB.getSeller(), lastSellerAmount * finalSellerDB.getCost());
				Player finalSeller = Bukkit.getServer().getPlayer(finalSellerDB.getSeller());
				if(finalSeller != null) finalSeller.sendMessage(ChatColor.GOLD + sender.getName() + ChatColor.BLUE + " bought " + ChatColor.GOLD + lastSellerAmount + ChatColor.BLUE + " of your shop's " + ChatColor.GOLD + material.toString() + ChatColor.BLUE + "!");
				plugin.getDatabase().save(DBClass);
				//Get a new instance of the Transaction Logging table and log the transaction
				DB_History transactionLog = new DB_History();
				transactionLog.setAmount(amount);
				transactionLog.setBuyer(sender.getName());
				transactionLog.setItem(material.toString());
				transactionLog.setTotalCost(totalCost);
				transactionLog.setCost(totalCost / amount);
				plugin.getDatabase().save(transactionLog);
				if(totalCost == 1) currencyName = plugin.economy.currencyNameSingular();
				else currencyName = plugin.economy.currencyNamePlural();
				sender.sendMessage(ChatColor.GREEN + "Successfully bought " + amount + " " + material.toString() + ". Total cost: " + totalCost + " " + currencyName);
			//If you get here, success!
			return true;
		}
		
		//Code for /find
		if(cmd.getName().equalsIgnoreCase("stock")) {
			//Permission Check
			if(!sender.hasPermission("dkabotshop.stock")) {
				sender.sendMessage(ChatColor.RED + "You lack permission to do this.");
				return true;
			}
			//Check input length
			if(args.length > 3) {
				sender.sendMessage(ChatColor.RED + "Too many arguments.");
				return true;
			}
			//Declare Variables
			Player player = (Player) sender;
			String seller = "";
			PagingList<DB_ForSale> DBPageList = null;
			Material material = null;
			List<DB_ForSale> DBClass = null;
			Integer hyphenCount;
			Integer page = 0;
			String hyphens = "";
			//In the event of all arguments, the second easiest one to do.
			if(args.length == 3) {
				//Get seller string
				seller = args[0];
				//Get Material
				material = plugin.getMaterial(args[1], true, player);
				//Check if a valid material
				if(material == null) {
					sender.sendMessage(ChatColor.RED + "Invalid item!");
					return true;
				}
				//Not smuggling illegal items today!
				if(plugin.illegalItem(material) == 1) {
					sender.sendMessage(ChatColor.RED + "Disallowed item!");
					return true;
				}
				//Get page number
				if(!plugin.isInt(args[2])) {
					sender.sendMessage(ChatColor.RED + "Page number must be an integer.");
					return true;
				}
				page = Integer.parseInt(args[2]) - 1;
				DBPageList = plugin.getDatabase().find(DB_ForSale.class).where().ieq("seller", seller).eq("item", material.toString()).orderBy().desc("id").findPagingList(8);
			}
			
			//In the event of two arguments, harder to do.
			if(args.length == 2) {
				//Try second argument as the page number
				if(plugin.isInt(args[1])) page = Integer.parseInt(args[1]) - 1;
				//Should it fail, it must be a material
				else {
					//Try second argument as a material
					material = plugin.getMaterial(args[1], true, player);
					//Someone is doing it wrong
					if(material == null) {
						sender.sendMessage(ChatColor.RED + "Invalid item / page number!");
						return true;
					}
					//Thou shalt not use illegal items
					if(plugin.illegalItem(material) == 1) {
						sender.sendMessage(ChatColor.RED + "Disallowed item!");
						return true;
					}
				}
				//Now to parse the first argument
				//If the material is already set, it has to be seller
				if(material != null) seller = args[0];
				//Otherwise, we get to play the guessing game again!
				else {
					//Try first argument as an item first, I doubt a player name will match an item
					material = plugin.getMaterial(args[1], true, player);
					//Not a material, has to be a player
					if(material == null) seller = args[0];
					//NO! To your illegal items
					else if(plugin.illegalItem(material) == 1)	{
						sender.sendMessage(ChatColor.RED + "Disallowed item!");
						return true;
					}
				}
				if(material != null && seller != "") DBPageList = plugin.getDatabase().find(DB_ForSale.class).where().ieq("seller", seller).eq("item", material.toString()).orderBy().desc("id").findPagingList(8);
				else if(material != null) DBPageList = plugin.getDatabase().find(DB_ForSale.class).where().eq("item", material.toString()).orderBy().desc("id").findPagingList(8);
				else if(seller != "") DBPageList = plugin.getDatabase().find(DB_ForSale.class).where().ieq("seller", seller).orderBy().desc("id").findPagingList(8);
				else {
					sender.sendMessage(ChatColor.RED + "Invalid arguments!");
					return true;
				}
			}
			
			//In case of one argument, the hardest
			if(args.length == 1) {
				//Try it as a material first
				material = plugin.getMaterial(args[0], true, player);
				//Out with ye, requester of illegal items!
				if(material != null) {
					if(plugin.illegalItem(material) == 1) {
						sender.sendMessage(ChatColor.RED + "Disallowed item!");
						return true;
					}
					//Set DBPageList for an item
					DBPageList = plugin.getDatabase().find(DB_ForSale.class).where().eq("item", material.toString()).orderBy().desc("id").findPagingList(8);
				}
				//Try for seller and page
				else {
					//Try for seller, messy but it works
					if(!plugin.getDatabase().find(DB_ForSale.class).where().ieq("seller", args[0]).findList().isEmpty()) {
						//Set seller
						seller = args[0];
						//Set the DBPageList for a seller
						DBPageList = plugin.getDatabase().find(DB_ForSale.class).where().ieq("seller", seller).orderBy().desc("id").findPagingList(8);
					}
					//Must be a page number
					else {
						//...or not
						if(!plugin.isInt(args[0])) {
							sender.sendMessage("Invalid argument!");
							return true;
						}
						//Set page number and get all the info
						else {
							page = Integer.parseInt(args[0]) - 1;
							DBPageList = plugin.getDatabase().find(DB_ForSale.class).orderBy().desc("id").findPagingList(8);
						}
					}
				}
			}
			
			//In case of no arguments, the easiest
			if(args.length == 0) {
				//Get all the info, that's all that has to be done.
				DBPageList = plugin.getDatabase().find(DB_ForSale.class).orderBy().desc("id").findPagingList(8);
			}
			
			//Page number validation
			if(page < 0) {
				sender.sendMessage(ChatColor.RED + "Invalid page number!");
				return true;
			}
			//Get the page worth of info
			DBClass = DBPageList.getPage(page).getList();
			
			if(DBClass.isEmpty()) {
				String message = "";
				if(material != null && seller != "") message = seller + " is not selling any " + material.toString();
				else if(material != null) message = "Nobody is selling any " + material.toString();
				else if(seller != "") message = seller + " is not selling anything";
				else message = "Nobody is selling anything";
				sender.sendMessage(ChatColor.RED + message);
				return true;
			}

			hyphenCount = ((40 - (" Page " + (page + 1) + " ").length()) / 2);
			for(Integer i = 0; i < hyphenCount;) {
				hyphens = hyphens + "-";
				i++;
			}
			if(seller != "" && material != null) sender.sendMessage(ChatColor.GREEN + "Items For Sale, " + material.toString() + " Sold By " + seller + " Only:");
			else if(seller != "") sender.sendMessage(ChatColor.GREEN + "Items For Sale, " + seller + " Only:");
			else if(material != null) sender.sendMessage(ChatColor.GREEN + "Items For Sale, " + material.toString() + " Only:");
			else sender.sendMessage(ChatColor.GREEN + "Items For Sale, No Filter:");
			sender.sendMessage(ChatColor.RED + hyphens + ChatColor.GRAY + " Page " + ChatColor.RED + (page + 1) + " " + hyphens);
			for(Integer i = 0; i < DBClass.size();) {
				DB_ForSale DB = DBClass.get(i);
				String currencyName;
				if(DB.getCost() == 1) currencyName = plugin.economy.currencyNameSingular();
				else currencyName = plugin.economy.currencyNamePlural();
				sender.sendMessage(ChatColor.GOLD + DB.getSeller() + ChatColor.BLUE + ": " + ChatColor.GOLD + DB.getAmount() + " " + DB.getItem() + ChatColor.BLUE + " for " + ChatColor.GOLD + DB.getCost() + " " + currencyName + ChatColor.BLUE + " each.");
				i++;
			}
			if(DBPageList.getPage(page).hasNext()) sender.sendMessage(ChatColor.GREEN + "There is a next page in this list!");
			return true;
		}
		//If you get here, you are the new Chuck Norris!
		return false;
	}

}
