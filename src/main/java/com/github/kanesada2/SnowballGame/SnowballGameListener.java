package com.github.kanesada2.SnowballGame;

import java.util.Collection;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.projectiles.BlockProjectileSource;

public class SnowballGameListener implements Listener {

	private SnowballGame plugin;

	public SnowballGameListener(SnowballGame plugin) {
        this.plugin = plugin;
    }
	@EventHandler(priority = EventPriority.LOW)
	public void onDispense(BlockDispenseEvent event){
		if(!(plugin.getConfig().getBoolean("Ball.Enabled_Ball") && event.getBlock().getType() == Material.DISPENSER && Util.isBall(event.getItem()))){
			return;
		}
		Dispenser from = (Dispenser)event.getBlock().getState();
		from.setMetadata("ballType", new FixedMetadataValue(plugin, Util.getBallType(event.getItem().getItemMeta().getLore())));
	}
	@EventHandler(priority = EventPriority.LOW)
	public void onProjectileLaunch(ProjectileLaunchEvent event) {
		Projectile projectile = event.getEntity();
		if(!(projectile instanceof Snowball)){
			return;
		}
		if(projectile.getShooter() instanceof Player){
			Player player = (Player)projectile.getShooter();
			ItemStack mainHand =  player.getInventory().getItemInMainHand();
			ItemStack offHand =  player.getInventory().getItemInOffHand();
			if(Util.isBall(mainHand)){
				projectile.setMetadata("ballType", new FixedMetadataValue(plugin, Util.getBallType(mainHand.getItemMeta().getLore())));
			}else if(Util.isBall(offHand) && mainHand.getType() != Material.SNOW_BALL){
				projectile.setMetadata("ballType", new FixedMetadataValue(plugin, Util.getBallType(offHand.getItemMeta().getLore())));
			}
			projectile.setGlowing(true);
		}else if(projectile.getShooter() instanceof BlockProjectileSource){
			BlockProjectileSource source = (BlockProjectileSource)projectile.getShooter();
			Block from = source.getBlock();
			if(from.hasMetadata("ballType")){
				projectile.setMetadata("ballType", new FixedMetadataValue(plugin, from.getMetadata("ballType").get(0).asString()));
				projectile.setGlowing(true);
				from.removeMetadata("ballType", plugin);
			}
		}
	}
	@EventHandler(priority = EventPriority.LOW)
	public void onProjectileHit(ProjectileHitEvent event) {
		 Projectile projectile = event.getEntity();
		 if (!(projectile instanceof Snowball)) {
	            return;
	        }
		 if((projectile.hasMetadata("ballType"))){
			 ItemStack ball = Util.getBall(projectile.getMetadata("ballType").get(0).asString());
			 if(event.getHitEntity() instanceof Player){
				Player player = (Player)event.getHitEntity();
				PlayerInventory inventory = player.getInventory();
				ItemStack offHand = inventory.getItemInOffHand();
				if(plugin.getConfig().getBoolean("Glove.Enabled_Glove") && Util.isGlove(offHand)){
					if(inventory.containsAtLeast(ball,1) || inventory.firstEmpty() != -1){
						inventory.addItem(ball);
						return;
					}
				} else {
					if(plugin.getConfig().getBoolean("Knockback_For_Players") && player.getGameMode() != GameMode.CREATIVE)
					Util.causeKnockBack(player,projectile);
				}
			 }
			 if(event.getHitBlock() != null && projectile.getVelocity().length() > 0.2){
				Projectile bounced = BallProcess.bounce(projectile,event.getHitBlock());
				bounced.setMetadata("ballType", new FixedMetadataValue(plugin, projectile.getMetadata("ballType").get(0).asString()));
				return;
			 }
			 //Bukkit.getLogger().info(String.valueOf(projectile.getLocation().distance(new Location(projectile.getWorld(), 100,5,100))));
			 projectile.getWorld().dropItem(projectile.getLocation(), ball);
			} else {
			Entity hitEntity = event.getHitEntity();
			if(hitEntity instanceof Player){
				Player player = (Player)hitEntity;
				if(player.getGameMode() != GameMode.CREATIVE){
					Util.causeKnockBack(player, projectile);
				}
			}
		}
	}
	@EventHandler(priority = EventPriority.LOW)
	public void onShootBow(EntityShootBowEvent event){
		if(!(plugin.getConfig().getBoolean("Bat.Enabled_Bat") && event.getEntity() instanceof Player && Util.isBat(event.getBow()))){
			return;
		}
		Entity batArrow = event.getProjectile();
		float force = event.getForce();
		Location impactLoc = batArrow.getLocation().add(batArrow.getVelocity().multiply(1/force));
		Entity meetCursor = impactLoc.getWorld().spawnEntity(impactLoc, EntityType.SNOWBALL);
		meetCursor.setGravity(false);
		meetCursor.remove();
		Collection <Entity> nearByEntities = impactLoc.getWorld().getNearbyEntities(impactLoc, 1.3, 1.3, 1.3);
		for (Entity entity : nearByEntities) {
			if(entity.getType() == EntityType.SNOWBALL && entity.hasMetadata("ballType")){
				BallProcess.hit((Projectile)entity,impactLoc , force);
				break;
			}
		}
		Player player = (Player)event.getEntity();
		if(force > 0.7){
			Location playerLoc = player.getLocation();
			playerLoc.setY(impactLoc.getY());
			player.getWorld().playSound(playerLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, force, 1);
			player.spawnParticle(Particle.SWEEP_ATTACK, playerLoc, 1);
		}
		event.setCancelled(true);
	}
	@EventHandler(priority = EventPriority.LOW)
	public void onTeeInteracted(PlayerInteractEvent event){
		if(!(event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock().getType() == Material.BREWING_STAND)){
			return;
		}
		ItemStack hand;
		if(Util.isBall(event.getPlayer().getInventory().getItemInMainHand())){
			hand = event.getPlayer().getInventory().getItemInMainHand();
		}else if(Util.isBall(event.getPlayer().getInventory().getItemInOffHand())){
			hand = event.getPlayer().getInventory().getItemInOffHand();
		}else{
			return;
		}
		event.setCancelled(true);
		Block tee = event.getClickedBlock();
		Location ballLoc = tee.getLocation();
		ballLoc.setX(ballLoc.getX() + 0.5);
		ballLoc.setY(ballLoc.getY() + 1.1);
		ballLoc.setZ(ballLoc.getZ() + 0.5);
		Collection <Entity> nearByEntities = ballLoc.getWorld().getNearbyEntities(ballLoc, 0.1, 0.1, 0.1);
		for (Entity entity : nearByEntities) {
			if(entity.getType() == EntityType.SNOWBALL && entity.hasMetadata("ballType")){
				return;
			}
		}
		Projectile placedBall = (Projectile)tee.getWorld().spawnEntity(ballLoc, EntityType.SNOWBALL);
		placedBall.setMetadata("ballType", new FixedMetadataValue(plugin, Util.getBallType(hand.getItemMeta().getLore())));
		placedBall.setGravity(false);
		placedBall.setGlowing(true);
		if(event.getPlayer().getGameMode() == GameMode.CREATIVE){
			return;
		}
		hand.setAmount(hand.getAmount() - 1);
	}

}
