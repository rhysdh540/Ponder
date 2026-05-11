package net.createmod.catnip.outliner;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.createmod.catnip.render.BindableTexture;
import net.createmod.catnip.render.PonderRenderTypes;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class BlockClusterOutline extends Outline {

	private final Cluster cluster;
	private final Iterable<BlockPos> positions;

	protected final Vector3f pos0Temp = new Vector3f();
	protected final Vector3f pos1Temp = new Vector3f();
	protected final Vector3f pos2Temp = new Vector3f();
	protected final Vector3f pos3Temp = new Vector3f();
	protected final Vector3f normalTemp = new Vector3f();
	protected final Vector3f originTemp = new Vector3f();

	public BlockClusterOutline(Iterable<BlockPos> positions) {
		this.positions = positions;
		cluster = new Cluster();
		cluster.includeAll(positions);
	}

	public boolean isSameSelection(Iterable<BlockPos> positions) {
		return this.positions == positions;
	}

	@Override
	public void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt) {
		params.loadColor(colorTemp);
		Vector4f color = colorTemp;
		int lightmap = params.lightmap;
		boolean disableLineNormals = params.disableLineNormals;

		renderFaces(ms, buffer, camera, pt, color, lightmap);
		renderEdges(ms, buffer, camera, pt, color, lightmap, disableLineNormals);
	}

	protected void renderFaces(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt, Vector4f color, int lightmap) {
		BindableTexture faceTexture = params.faceTexture;
		if (faceTexture == null)
			return;
		if (cluster.isEmpty())
			return;

		ms.pushPose();
		ms.translate(cluster.anchor.getX() - camera.x, cluster.anchor.getY() - camera.y,
			cluster.anchor.getZ() - camera.z);

		PoseStack.Pose pose = ms.last();
		RenderType renderType = PonderRenderTypes.outlineTranslucent(faceTexture.getLocation(), true);
		VertexConsumer consumer = buffer.getLateBuffer(renderType);

		cluster.visibleFaces.forEach((face, axisDirection) -> {
			Direction direction = Direction.get(axisDirection, face.axis);
			BlockPos pos = face.pos;
			if (axisDirection == AxisDirection.POSITIVE)
				pos = pos.relative(direction.getOpposite());
			bufferBlockFace(pose, consumer, pos, direction, color, lightmap);
		});

		ms.popPose();
	}

	protected void renderEdges(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt, Vector4f color, int lightmap, boolean disableNormals) {
		float lineWidth = params.getLineWidth();
		if (lineWidth == 0)
			return;
		if (cluster.isEmpty())
			return;

		ms.pushPose();
		ms.translate(cluster.anchor.getX() - camera.x, cluster.anchor.getY() - camera.y,
			cluster.anchor.getZ() - camera.z);

		PoseStack.Pose pose = ms.last();
		VertexConsumer consumer = buffer.getBuffer(PonderRenderTypes.outlineSolid());

		cluster.visibleEdges.forEach(edge -> {
			BlockPos pos = edge.pos;
			Vector3f origin = originTemp;
			origin.set(pos.getX(), pos.getY(), pos.getZ());
			Direction direction = Direction.get(AxisDirection.POSITIVE, edge.axis);
			bufferCuboidLine(pose, consumer, origin, direction, 1, lineWidth, color, lightmap, disableNormals);
		});

		ms.popPose();
	}

	public static void loadFaceData(Direction face, Vector3f pos0, Vector3f pos1, Vector3f pos2, Vector3f pos3, Vector3f normal) {
		switch (face) {
			case DOWN -> {
				// 0 1 2 3
				pos0.set(0, 0, 1);
				pos1.set(0, 0, 0);
				pos2.set(1, 0, 0);
				pos3.set(1, 0, 1);
				normal.set(0, -1, 0);
			}
			case UP -> {
				// 4 5 6 7
				pos0.set(0, 1, 0);
				pos1.set(0, 1, 1);
				pos2.set(1, 1, 1);
				pos3.set(1, 1, 0);
				normal.set(0, 1, 0);
			}
			case NORTH -> {
				// 7 2 1 4
				pos0.set(1, 1, 0);
				pos1.set(1, 0, 0);
				pos2.set(0, 0, 0);
				pos3.set(0, 1, 0);
				normal.set(0, 0, -1);
			}
			case SOUTH -> {
				// 5 0 3 6
				pos0.set(0, 1, 1);
				pos1.set(0, 0, 1);
				pos2.set(1, 0, 1);
				pos3.set(1, 1, 1);
				normal.set(0, 0, 1);
			}
			case WEST -> {
				// 4 1 0 5
				pos0.set(0, 1, 0);
				pos1.set(0, 0, 0);
				pos2.set(0, 0, 1);
				pos3.set(0, 1, 1);
				normal.set(-1, 0, 0);
			}
			case EAST -> {
				// 6 3 2 7
				pos0.set(1, 1, 1);
				pos1.set(1, 0, 1);
				pos2.set(1, 0, 0);
				pos3.set(1, 1, 0);
				normal.set(1, 0, 0);
			}
		}
	}

	public static void addPos(float x, float y, float z, Vector3f pos0, Vector3f pos1, Vector3f pos2, Vector3f pos3) {
		pos0.add(x, y, z);
		pos1.add(x, y, z);
		pos2.add(x, y, z);
		pos3.add(x, y, z);
	}

	protected void bufferBlockFace(PoseStack.Pose pose, VertexConsumer consumer, BlockPos pos, Direction face, Vector4f color, int lightmap) {
		Vector3f pos0 = pos0Temp;
		Vector3f pos1 = pos1Temp;
		Vector3f pos2 = pos2Temp;
		Vector3f pos3 = pos3Temp;
		Vector3f normal = normalTemp;

		loadFaceData(face, pos0, pos1, pos2, pos3, normal);
		addPos(pos.getX() + face.getStepX() / 128f,
			pos.getY() + face.getStepY() / 128f,
			pos.getZ() + face.getStepZ() / 128f,
			pos0, pos1, pos2, pos3);

		bufferQuad(pose, consumer, pos0, pos1, pos2, pos3, color, lightmap, normal);
	}

	private static class Cluster {

		@Nullable
		private BlockPos anchor;
		private final Map<MergeEntry, AxisDirection> visibleFaces;
		private final Set<MergeEntry> visibleEdges;

		public Cluster() {
			visibleEdges = new HashSet<>();
			visibleFaces = new HashMap<>();
			anchor = null;
		}

		public boolean isEmpty() {
			return anchor == null;
		}

		public void includeAll(Iterable<BlockPos> positions) {
			Iterator<BlockPos> iterator = positions.iterator();
			if (!iterator.hasNext())
				return;

			anchor = iterator.next();

			IntList xs = new IntArrayList();
			IntList ys = new IntArrayList();
			IntList zs = new IntArrayList();

			xs.add(0);
			ys.add(0);
			zs.add(0);

			int minX = 0, minY = 0, minZ = 0;
			int maxX = 0, maxY = 0, maxZ = 0;

			int anchorX = anchor.getX();
			int anchorY = anchor.getY();
			int anchorZ = anchor.getZ();

			while (iterator.hasNext()) {
				BlockPos pos = iterator.next();
				int x = pos.getX() - anchorX;
				int y = pos.getY() - anchorY;
				int z = pos.getZ() - anchorZ;

				xs.add(x);
				ys.add(y);
				zs.add(z);

				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				minZ = Math.min(minZ, z);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
				maxZ = Math.max(maxZ, z);
			}

			int xOffset = -minX;
			int yOffset = -minY;
			int zOffset = -minZ;
			int sizeX = maxX - minX + 1;
			int sizeY = maxY - minY + 1;
			int sizeZ = maxZ - minZ + 1;

			BitSet occupancy = new BitSet(sizeX * sizeY * sizeZ);
			for (int i = 0; i < xs.size(); i++) {
				int x = xs.getInt(i) + xOffset;
				int y = ys.getInt(i) + yOffset;
				int z = zs.getInt(i) + zOffset;
				occupancy.set(index(x, y, z, sizeX, sizeY));
			}

			buildFaces(occupancy, sizeX, sizeY, sizeZ, xOffset, yOffset, zOffset);
			buildEdges(occupancy, sizeX, sizeY, sizeZ, xOffset, yOffset, zOffset);
		}

		private void buildFaces(BitSet occupancy, int sizeX, int sizeY, int sizeZ, int xOffset, int yOffset, int zOffset) {
			for (int x = 0; x <= sizeX; x++) {
				for (int y = 0; y < sizeY; y++) {
					for (int z = 0; z < sizeZ; z++) {
						boolean negativeOccupied = isOccupied(occupancy, x - 1, y, z, sizeX, sizeY, sizeZ);
						boolean positiveOccupied = isOccupied(occupancy, x, y, z, sizeX, sizeY, sizeZ);
						if (negativeOccupied == positiveOccupied)
							continue;

						addFace(Axis.X, x - xOffset, y - yOffset, z - zOffset,
							negativeOccupied ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE);
					}
				}
			}

			for (int y = 0; y <= sizeY; y++) {
				for (int x = 0; x < sizeX; x++) {
					for (int z = 0; z < sizeZ; z++) {
						boolean negativeOccupied = isOccupied(occupancy, x, y - 1, z, sizeX, sizeY, sizeZ);
						boolean positiveOccupied = isOccupied(occupancy, x, y, z, sizeX, sizeY, sizeZ);
						if (negativeOccupied == positiveOccupied)
							continue;

						addFace(Axis.Y, x - xOffset, y - yOffset, z - zOffset,
							negativeOccupied ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE);
					}
				}
			}

			for (int z = 0; z <= sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					for (int y = 0; y < sizeY; y++) {
						boolean negativeOccupied = isOccupied(occupancy, x, y, z - 1, sizeX, sizeY, sizeZ);
						boolean positiveOccupied = isOccupied(occupancy, x, y, z, sizeX, sizeY, sizeZ);
						if (negativeOccupied == positiveOccupied)
							continue;

						addFace(Axis.Z, x - xOffset, y - yOffset, z - zOffset,
							negativeOccupied ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE);
					}
				}
			}
		}

		private void buildEdges(BitSet occupancy, int sizeX, int sizeY, int sizeZ, int xOffset, int yOffset, int zOffset) {
			for (int x = 0; x < sizeX; x++) {
				for (int y = 0; y <= sizeY; y++) {
					for (int z = 0; z <= sizeZ; z++) {
						int occupiedCount = 0;
						if (isOccupied(occupancy, x, y - 1, z - 1, sizeX, sizeY, sizeZ))
							occupiedCount++;
						if (isOccupied(occupancy, x, y - 1, z, sizeX, sizeY, sizeZ))
							occupiedCount++;
						if (isOccupied(occupancy, x, y, z - 1, sizeX, sizeY, sizeZ))
							occupiedCount++;
						if (isOccupied(occupancy, x, y, z, sizeX, sizeY, sizeZ))
							occupiedCount++;

						if ((occupiedCount & 1) != 0)
							addEdge(Axis.X, x - xOffset, y - yOffset, z - zOffset);
					}
				}
			}

			for (int y = 0; y < sizeY; y++) {
				for (int x = 0; x <= sizeX; x++) {
					for (int z = 0; z <= sizeZ; z++) {
						int occupiedCount = 0;
						if (isOccupied(occupancy, x - 1, y, z - 1, sizeX, sizeY, sizeZ))
							occupiedCount++;
						if (isOccupied(occupancy, x, y, z - 1, sizeX, sizeY, sizeZ))
							occupiedCount++;
						if (isOccupied(occupancy, x - 1, y, z, sizeX, sizeY, sizeZ))
							occupiedCount++;
						if (isOccupied(occupancy, x, y, z, sizeX, sizeY, sizeZ))
							occupiedCount++;

						if ((occupiedCount & 1) != 0)
							addEdge(Axis.Y, x - xOffset, y - yOffset, z - zOffset);
					}
				}
			}

			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x <= sizeX; x++) {
					for (int y = 0; y <= sizeY; y++) {
						int occupiedCount = 0;
						if (isOccupied(occupancy, x - 1, y - 1, z, sizeX, sizeY, sizeZ))
							occupiedCount++;
						if (isOccupied(occupancy, x, y - 1, z, sizeX, sizeY, sizeZ))
							occupiedCount++;
						if (isOccupied(occupancy, x - 1, y, z, sizeX, sizeY, sizeZ))
							occupiedCount++;
						if (isOccupied(occupancy, x, y, z, sizeX, sizeY, sizeZ))
							occupiedCount++;

						if ((occupiedCount & 1) != 0)
							addEdge(Axis.Z, x - xOffset, y - yOffset, z - zOffset);
					}
				}
			}
		}

		private void addFace(Axis axis, int x, int y, int z, AxisDirection axisDirection) {
			visibleFaces.put(new MergeEntry(axis, new BlockPos(x, y, z)), axisDirection);
		}

		private void addEdge(Axis axis, int x, int y, int z) {
			visibleEdges.add(new MergeEntry(axis, new BlockPos(x, y, z)));
		}

		private static int index(int x, int y, int z, int sizeX, int sizeY) {
			return x + sizeX * (y + sizeY * z);
		}

		private static boolean isOccupied(BitSet occupancy, int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
			if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ)
				return false;
			return occupancy.get(index(x, y, z, sizeX, sizeY));
		}

	}

	private static class MergeEntry {

		private final Axis axis;
		private final BlockPos pos;

		public MergeEntry(Axis axis, BlockPos pos) {
			this.axis = axis;
			this.pos = pos;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof MergeEntry other))
				return false;

			return this.axis == other.axis && this.pos.equals(other.pos);
		}

		@Override
		public int hashCode() {
			return this.pos.hashCode() * 31 + axis.ordinal();
		}
	}

}
