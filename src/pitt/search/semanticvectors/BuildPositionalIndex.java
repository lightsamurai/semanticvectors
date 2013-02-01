/**
   Copyright (c) 2008, University of Pittsburgh

   All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

 * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following
   disclaimer in the documentation and/or other materials provided
   with the distribution.

 * Neither the name of the University of Pittsburgh nor the names
   of its contributors may be used to endorse or promote products
   derived from this software without specific prior written
   permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
   CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
   PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **/

package pitt.search.semanticvectors;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

import pitt.search.semanticvectors.vectors.VectorType;

/**
 * Command line utility for creating semantic vector indexes using the
 * sliding context window approach (see work on HAL, and by Schutze).
 */
public class BuildPositionalIndex {
  public static final Logger logger = Logger.getLogger(
      BuildPositionalIndex.class.getCanonicalName());
  static VectorStore newBasicTermVectors = null;

  public static String usageMessage =
    "BuildPositionalIndex class in package pitt.search.semanticvectors"
    + "\nUsage: java pitt.search.semanticvectors.BuildPositionalIndex -luceneindexpath PATH_TO_LUCENE_INDEX"
    + "\nBuildPositionalIndex creates file termtermvectors.bin in local directory."
    + "\nOther parameters that can be changed include"
    + "\n    windowlength (size of sliding context window),"
    + "\n    dimension (number of dimensions), vectortype (real, complex, binary)"
    + "\n    seedlength (number of non-zero entries in basic vectors),"
    + "\n    minimum term frequency.\n"
    + "\nTo change these use the command line arguments "
    + "\n  -vectortype [real, complex, or binary]"
    + "\n  -dimension [number of dimensions]"
    + "\n  -seedlength [seed length]"
    + "\n  -minfrequency [minimum term frequency]"
    + "\n  -initialtermvectors [name of preexisting vectorstore for term vectors]"
    + "\n  -windowradius [window size]"
    + "\n  -positionalmethod [positional indexing method: basic (default), directional (HAL), permutation (Sahlgren 2008)";

  /**
   * Builds term vector stores from a Lucene index - this index must
   * contain TermPositionVectors.
   * @param args
   */
  public static void main (String[] args) throws IllegalArgumentException {
    FlagConfig flagConfig;
    try {
      flagConfig = FlagConfig.getFlagConfig(args);
      args = flagConfig.remainingArgs;
    } catch (IllegalArgumentException e) {
      System.out.println(usageMessage);
      throw e;
    }

    if (flagConfig.luceneindexpath().isEmpty()) {
      throw (new IllegalArgumentException("-luceneindexpath must be set."));
    }
    String luceneIndex = flagConfig.luceneindexpath();

    // If initialtermvectors is defined, read these vectors.
    if (!flagConfig.initialtermvectors().isEmpty()) {
      try {
        VectorStoreRAM vsr = new VectorStoreRAM(flagConfig);
        vsr.initFromFile(flagConfig.initialtermvectors());
        newBasicTermVectors = vsr;
        VerbatimLogger.info("Using trained index vectors from vector store " + flagConfig.initialtermvectors());
      } catch (IOException e) {
        logger.info("Could not read from vector store " + flagConfig.initialtermvectors());
        System.out.println(usageMessage);
        throw new IllegalArgumentException();
      }
    }

    String termFile = flagConfig.termtermvectorsfile();
    String docFile = flagConfig.docvectorsfile();

    if (flagConfig.positionalmethod().equals("permutation")) termFile = flagConfig.permutedvectorfile();
    else if (flagConfig.positionalmethod().equals("permutation_plus_basic")) termFile = flagConfig.permplustermvectorfile();
    else if (flagConfig.positionalmethod().equals("directional")) termFile = flagConfig.directionalvectorfile();

    VerbatimLogger.info("Building positional index, Lucene index: " + luceneIndex
        + ", Seedlength: " + flagConfig.seedlength()
        + ", Vector length: " + flagConfig.dimension()
        + ", Vector type: " + flagConfig.vectortype()
        + ", Minimum term frequency: " + flagConfig.minfrequency()
        + ", Maximum term frequency: " + flagConfig.maxfrequency()
        + ", Number non-alphabet characters: " + flagConfig.maxnonalphabetchars()
        + ", Window radius: " + flagConfig.windowradius()
        + ", Fields to index: " + Arrays.toString(flagConfig.contentsfields())
        + "\n");

    try {
      TermTermVectorsFromLucene vecStore = new TermTermVectorsFromLucene(
          flagConfig,
          luceneIndex,  flagConfig.vectortype(),
          flagConfig.dimension(), flagConfig.seedlength(), flagConfig.minfrequency(), flagConfig.maxfrequency(),
          flagConfig.maxnonalphabetchars(), flagConfig.filteroutnumbers(), 2 * flagConfig.windowradius() + 1, flagConfig.positionalmethod(),
          newBasicTermVectors, flagConfig.contentsfields());
      
      VectorStoreWriter.writeVectors(termFile, flagConfig, vecStore);

      for (int i = 1; i < flagConfig.trainingcycles(); ++i) {
        newBasicTermVectors = vecStore.getBasicTermVectors();
        VerbatimLogger.info("\nRetraining with learned term vectors ...");
        vecStore = new TermTermVectorsFromLucene(
            flagConfig,
            luceneIndex,  flagConfig.vectortype(),
            flagConfig.dimension(), flagConfig.seedlength(), flagConfig.minfrequency(), flagConfig.maxfrequency(),
            flagConfig.maxnonalphabetchars(), flagConfig.filteroutnumbers(), 2 * flagConfig.windowradius() + 1, flagConfig.positionalmethod(),
            newBasicTermVectors, flagConfig.contentsfields());
      }

      if (flagConfig.trainingcycles() > 1) {
        termFile = termFile.replaceAll("\\..*", "") + flagConfig.trainingcycles() + ".bin";
        docFile = "docvectors" + flagConfig.trainingcycles() + ".bin";
        VectorStoreWriter.writeVectors(termFile, flagConfig, vecStore);
      }

      if (!flagConfig.docindexing().equals("none")) {
        IncrementalDocVectors.createIncrementalDocVectors(
            vecStore, flagConfig, luceneIndex, flagConfig.contentsfields(), docFile);
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
