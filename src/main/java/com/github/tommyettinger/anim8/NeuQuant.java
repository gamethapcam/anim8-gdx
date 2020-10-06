package com.github.tommyettinger.anim8;
/*
 * NeuQuant Neural-Net Quantization Algorithm
 * ------------------------------------------
 *
 * Copyright (c) 1994 Anthony Dekker
 *
 * NEUQUANT Neural-Net quantization algorithm by Anthony Dekker, 1994. See
 * "Kohonen neural networks for optimal colour quantization" in "Network:
 * Computation in Neural Systems" Vol. 5 (1994) pp 351-367. for a discussion of
 * the algorithm.
 *
 * Any party obtaining a copy of these files from the author, directly or
 * indirectly, is granted, free of charge, a full and unrestricted irrevocable,
 * world-wide, paid up, royalty-free, nonexclusive right and license to deal in
 * this software and documentation files (the "Software"), including without
 * limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons who
 * receive copies from any such party to do so, with the only requirement being
 * that this copyright notice remain intact.
 */

import com.badlogic.gdx.graphics.Pixmap;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

//	 Ported to Java 12/00 K Weiner
public class NeuQuant {

	protected int limit; /* number of colours used */

	/* four primes near 500 - assume no image has a length so large */
	/* that it is divisible by all four primes */
	protected static final int prime1 = 499;

	protected static final int prime2 = 491;

	protected static final int prime3 = 487;

	protected static final int prime4 = 503;

	protected static final int minpicturebytes = (3 * prime4);

	/*
	 * Network Definitions -------------------
	 */

	protected int maxnetpos = (limit - 1);

	protected static final int netbiasshift = 4; /* bias for colour values */

	protected static final int ncycles = 100; /* no. of learning cycles */

	/* defs for freq and bias */
	protected static final int intbiasshift = 16; /* bias for fractions */

	protected static final int intbias = (1 << intbiasshift); // 65536

	protected static final int gammashift = 10;

	protected static final int gamma = (1 << gammashift); // 1024

	protected static final int betashift = 10;

	protected static final int beta = (intbias >> betashift); // 64

	protected static final int betagamma = (intbias << (gammashift - betashift)); // 65536

	/* defs for decreasing radius factor */
	protected int initrad; // for 256 cols, radius starts at 32.0 biased by 6 bits

	protected static final int radiusbiasshift = 6;
	protected static final int radiusbias = (1 << radiusbiasshift);

	protected int initradius; // and decreases by a factor of 1/30 each cycle

	protected static final int radiusdec = 30;

	/* defs for decreasing alpha factor */
	protected static final int alphabiasshift = 10; /* alpha starts at 1.0 */

	protected static final int initalpha = (1 << alphabiasshift);

	protected int alphadec; /* biased by 10 bits */

	/* radbias and alpharadbias used for radpower calculation */
	protected static final int radbiasshift = 8;

	protected static final int radbias = (1 << radbiasshift);

	protected static final int alpharadbshift = (alphabiasshift + radbiasshift);

	protected static final int alpharadbias = (1 << alpharadbshift);

	/*
	 * Types and Global Variables --------------------------
	 */

	protected IntBuffer thepicture; /* the input image itself */

	protected int lengthcount; /* lengthcount = H*W*3 */

	protected int samplefac; /* sampling factor 1..30 */

	// typedef int pixel[4]; /* BGRc */
	protected int[][] network; /* the network itself - [netsize][4] */

	/* for network lookup */
	protected int[] netindex = new int[256];

	protected int[] bias;

	/* bias and freq arrays for learning */
	protected int[] freq;

	protected int[] radpower;

	/* radpower for precomputation */

	/*
	 * Initialise network in range (0,0,0) to (255,255,255) and set parameters
	 * -----------------------------------------------------------------------
	 */
	public NeuQuant(Pixmap thepic, int sample, int limit) {
		this.limit = limit;
		initrad = (limit >> 3);
		initradius = (initrad * radiusbias);
		radpower = new int[initrad];
		bias = new int[limit];
		freq = new int[limit];
		int i;
		int[] p;
		lengthcount = thepic.getHeight() * thepic.getWidth();
		thepicture = IntBuffer.allocate(lengthcount);
		thepicture.put(thepic.getPixels() instanceof ByteBuffer ? thepic.getPixels().asIntBuffer() : thepic.getPixels() instanceof Buffer ? ((IntBuffer) (Buffer) thepic.getPixels()) : null);
		samplefac = sample;

		network = new int[limit][];
		for (i = 0; i < limit; i++) {
			network[i] = new int[4];
			p = network[i];
			p[0] = p[1] = p[2] = (i << (netbiasshift + 8)) / limit;
			freq[i] = intbias / limit; /* 1/netsize */
			bias[i] = 0;
		}
	}

	public NeuQuant(Pixmap[] thepic, int pixmapCount, int sample, int limit) {
		this.limit = limit;
		initrad = (limit >> 3);
		initradius = (initrad * radiusbias);
		radpower = new int[initrad];
		bias = new int[limit];
		freq = new int[limit];
		int i;
		int[] p;

		lengthcount = thepic[0].getWidth() * thepic[0].getHeight();
		thepicture = IntBuffer.allocate(pixmapCount * lengthcount);
		for (int j = 0; j < pixmapCount; j++) {
			thepicture.put(thepic[j].getPixels() instanceof ByteBuffer ? thepic[j].getPixels().asIntBuffer() : thepic[j].getPixels() instanceof Buffer ? ((IntBuffer) (Buffer) thepic[j].getPixels()) : null);
		}

		samplefac = sample;

		network = new int[limit][];
		for (i = 0; i < limit; i++) {
			network[i] = new int[4];
			p = network[i];
			p[0] = p[1] = p[2] = (i << (netbiasshift + 8)) / limit;
			freq[i] = intbias / limit; /* 1/netsize */
			bias[i] = 0;
		}
	}

	public void colorMap(int[] colorArray) {
		int[] index = new int[limit];
		for (int i = 0; i < limit; i++)
			index[network[i][3]] = i;
		int k = 1;
		for (int i = 0; i < limit; i++) {
			int j = index[i];
			colorArray[k++] = (network[j][2] << 24) | (network[j][1] << 16 & 0xFF0000) | (network[j][0] << 8 & 0xFF00) | 0xFF;
		}
	}

	/*
	 * Insertion sort of network and building of netindex[0..255] (to do after
	 * unbias)
	 * -------------------------------------------------------------------------------
	 */
	public void inxbuild() {

		int i, j, smallpos, smallval;
		int[] p;
		int[] q;
		int previouscol, startpos;

		previouscol = 0;
		startpos = 0;
		for (i = 0; i < limit; i++) {
			p = network[i];
			smallpos = i;
			smallval = p[1]; /* index on g */
			/* find smallest in i..netsize-1 */
			for (j = i + 1; j < limit; j++) {
				q = network[j];
				if (q[1] < smallval) { /* index on g */
					smallpos = j;
					smallval = q[1]; /* index on g */
				}
			}
			q = network[smallpos];
			/* swap p (i) and q (smallpos) entries */
			if (i != smallpos) {
				j = q[0];
				q[0] = p[0];
				p[0] = j;
				j = q[1];
				q[1] = p[1];
				p[1] = j;
				j = q[2];
				q[2] = p[2];
				p[2] = j;
				j = q[3];
				q[3] = p[3];
				p[3] = j;
			}
			/* smallval entry is now in position i */
			if (smallval != previouscol) {
				netindex[previouscol] = (startpos + i) >> 1;
				for (j = previouscol + 1; j < smallval; j++)
					netindex[j] = i;
				previouscol = smallval;
				startpos = i;
			}
		}
		netindex[previouscol] = (startpos + maxnetpos) >> 1;
		for (j = previouscol + 1; j < 256; j++)
			netindex[j] = maxnetpos; /* really 256 */
	}

	/*
	 * Main Learning Loop ------------------
	 */
	public void learn() {

		int i, j, b, g, r, color;
		int radius, rad, alpha, step, delta, samplepixels;
		int[] p;
		int pix, lim;

		if (lengthcount < minpicturebytes)
			samplefac = 1;
		alphadec = 30 + ((samplefac - 1) / 3);
		p = thepicture.array();
		pix = 0;
		lim = lengthcount;
		samplepixels = lengthcount / samplefac;
		delta = samplepixels / ncycles;
		alpha = initalpha;
		radius = initradius;

		rad = radius >> radiusbiasshift;
		if (rad <= 1)
			rad = 0;
		for (i = 0; i < rad; i++)
			radpower[i] = alpha * (((rad * rad - i * i) * radbias) / (rad * rad));

		// fprintf(stderr,"beginning 1D learning: initial radius=%d\n", rad);

		if (lengthcount < minpicturebytes)
			step = 1;
		else if ((lengthcount % prime1) != 0)
			step = prime1;
		else {
			if ((lengthcount % prime2) != 0)
				step = prime2;
			else {
				if ((lengthcount % prime3) != 0)
					step = prime3;
				else
					step = prime4;
			}
		}

		i = 0;
		while (i < samplepixels) {
			color = p[pix];
			b = (color >>> 8 & 0xff) << netbiasshift;
			g = (color >>> 16 & 0xff) << netbiasshift;
			r = (color >>> 24) << netbiasshift;
			j = contest(b, g, r);

			altersingle(alpha, j, b, g, r);
			if (rad != 0)
				alterneigh(rad, j, b, g, r); /* alter neighbours */

			pix += step;
			if (pix >= lim)
				pix -= lengthcount;

			i++;
			if (delta == 0)
				delta = 1;
			if (i % delta == 0) {
				alpha -= alpha / alphadec;
				radius -= radius / radiusdec;
				rad = radius >> radiusbiasshift;
				if (rad <= 1)
					rad = 0;
				for (j = 0; j < rad; j++)
					radpower[j] = alpha * (((rad * rad - j * j) * radbias) / (rad * rad));
			}
		}
	}

	/*
	 * Search for BGR values 0..255 (after net is unbiased) and return color index
	 */
	public int map(int b, int g, int r) {

		int i, j, dist, a, bestd;
		int[] p;
		int best;

		bestd = 1000; /* biggest possible dist is 256*3 */
		best = -1;
		i = netindex[g]; /* index on g */
		j = i - 1; /* start at netindex[g] and work outwards */

		while ((i < limit) || (j >= 0)) {
			if (i < limit) {
				p = network[i];
				dist = p[1] - g; /* inx key */
				if (dist >= bestd)
					i = limit; /* stop iter */
				else {
					i++;
					if (dist < 0)
						dist = -dist;
					a = p[0] - b;
					if (a < 0)
						a = -a;
					dist += a;
					if (dist < bestd) {
						a = p[2] - r;
						if (a < 0)
							a = -a;
						dist += a;
						if (dist < bestd) {
							bestd = dist;
							best = p[3];
						}
					}
				}
			}
			if (j >= 0) {
				p = network[j];
				dist = g - p[1]; /* inx key - reverse dif */
				if (dist >= bestd)
					j = -1; /* stop iter */
				else {
					j--;
					if (dist < 0)
						dist = -dist;
					a = p[0] - b;
					if (a < 0)
						a = -a;
					dist += a;
					if (dist < bestd) {
						a = p[2] - r;
						if (a < 0)
							a = -a;
						dist += a;
						if (dist < bestd) {
							bestd = dist;
							best = p[3];
						}
					}
				}
			}
		}
		return (best);
	}

	public void process(int[] colorArray) {
		learn();
		unbiasnet();
		inxbuild();
		colorMap(colorArray);
	}

	/*
	 * Unbias network to give byte values 0..255 and record position i to prepare
	 * for sort
	 * -----------------------------------------------------------------------------------
	 */
	public void unbiasnet() {

		int i;

		for (i = 0; i < limit; i++) {
			network[i][0] >>= netbiasshift;
			network[i][1] >>= netbiasshift;
			network[i][2] >>= netbiasshift;
			network[i][3] = i; /* record colour no */
		}
	}

	/*
	 * Move adjacent neurons by precomputed alpha*(1-((i-j)^2/[r]^2)) in
	 * radpower[|i-j|]
	 * ---------------------------------------------------------------------------------
	 */
	protected void alterneigh(int rad, int i, int b, int g, int r) {

		int j, k, lo, hi, a, m;
		int[] p;

		lo = i - rad;
		if (lo < -1)
			lo = -1;
		hi = i + rad;
		if (hi > limit)
			hi = limit;

		j = i + 1;
		k = i - 1;
		m = 1;
		while ((j < hi) || (k > lo)) {
			a = radpower[m++];
			if (j < hi) {
				p = network[j++];
				p[0] -= (a * (p[0] - b)) / alpharadbias;
				p[1] -= (a * (p[1] - g)) / alpharadbias;
				p[2] -= (a * (p[2] - r)) / alpharadbias;
			}
			if (k > lo) {
				p = network[k--];

				p[0] -= (a * (p[0] - b)) / alpharadbias;
				p[1] -= (a * (p[1] - g)) / alpharadbias;
				p[2] -= (a * (p[2] - r)) / alpharadbias;

			}
		}
	}

	/*
	 * Move neuron i towards biased (b,g,r) by factor alpha
	 * ----------------------------------------------------
	 */
	protected void altersingle(int alpha, int i, int b, int g, int r) {

		/* alter hit neuron */
		int[] n = network[i];
		n[0] -= (alpha * (n[0] - b)) / initalpha;
		n[1] -= (alpha * (n[1] - g)) / initalpha;
		n[2] -= (alpha * (n[2] - r)) / initalpha;
	}

	/*
	 * Search for biased BGR values ----------------------------
	 */
	protected int contest(int b, int g, int r) {

		/* finds closest neuron (min dist) and updates freq */
		/* finds best neuron (min dist-bias) and returns position */
		/* for frequently chosen neurons, freq[i] is high and bias[i] is negative */
		/* bias[i] = gamma*((1/netsize)-freq[i]) */

		int i, dist, a, biasdist, betafreq;
		int bestpos, bestbiaspos, bestd, bestbiasd;
		int[] n;

		bestd = ~(1 << 31);
		bestbiasd = bestd;
		bestpos = -1;
		bestbiaspos = bestpos;

		for (i = 0; i < limit; i++) {
			n = network[i];
			dist = n[0] - b;
			if (dist < 0)
				dist = -dist;
			a = n[1] - g;
			if (a < 0)
				a = -a;
			dist += a;
			a = n[2] - r;
			if (a < 0)
				a = -a;
			dist += a;
			if (dist < bestd) {
				bestd = dist;
				bestpos = i;
			}
			biasdist = dist - ((bias[i]) >> (intbiasshift - netbiasshift));
			if (biasdist < bestbiasd) {
				bestbiasd = biasdist;
				bestbiaspos = i;
			}
			betafreq = (freq[i] >> betashift);
			freq[i] -= betafreq;
			bias[i] += (betafreq << gammashift);
		}
		freq[bestpos] += beta;
		bias[bestpos] -= betagamma;
		return (bestbiaspos);
	}
}
