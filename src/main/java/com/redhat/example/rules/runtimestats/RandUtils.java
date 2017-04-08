/*
 * Copyright 2016 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.example.rules.runtimestats;

import java.util.Random;

public class RandUtils {
	/**
	 * Randomly choose a value from the specified list regarding the specified rates.
	 * @param values list of candidate values
	 * @param list of each values' rate. if null, all candidates have equal rates.
	 * @param rand Random instance
	 * @return selected value
	 */
	public static <T> T ChooseOne(T values[], int rates[], Random rand) {
		int num = values.length;
		int sumRate = 0;
		if (rates == null) {
			sumRate = values.length;
		} else {
			for (int i=0; i < num; i++) {
				sumRate += (rates.length <= i) ? 0 : rates[i];
			}
		}
		int randVal = rand.nextInt(sumRate);
		sumRate = 0;
		for (int i=0; i < num; i++) {
			int rate = rates == null ? 1 : rates[i];
			if (sumRate <= randVal && randVal < sumRate + rate) {
				return values[i];
			}
			sumRate += rate;
		}
		return null;
	}

}
