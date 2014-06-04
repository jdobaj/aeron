/*
 * Copyright 2014 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.aeron.util;

import org.junit.Test;
import org.mockito.InOrder;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static java.lang.Integer.valueOf;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.*;

public class AtomicArrayTest
{
    @Test
    public void shouldHandleAddToEmptyArray()
    {
        final AtomicArray<Integer> array = new AtomicArray<>();

        array.add(10);
        assertThat(array.length(), is(1));
        assertThat(array.get(0), is(10));
    }

    @Test
    public void shouldHandleAddToNonEmptyArray()
    {
        final AtomicArray<Integer> array = new AtomicArray<>();

        array.add(10);
        array.add(20);

        assertThat(array.length(), is(2));
        assertThat(array.get(0), is(10));
        assertThat(array.get(1), is(20));
    }

    @Test
    public void shouldHandleRemoveFromEmptyArray()
    {
        final AtomicArray<Integer> array = new AtomicArray<>();

        array.remove(10);

        assertThat(array.length(), is(0));
    }

    @Test
    public void shouldHandleRemoveFromOneElementArray()
    {
        final AtomicArray<Integer> array = new AtomicArray<>();

        array.add(10);
        array.remove(10);

        assertThat(array.length(), is(0));
    }

    @Test
    public void shouldHandleRemoveOfNonExistentElementFromOneElementArray()
    {
        final AtomicArray<Integer> array = new AtomicArray<>();

        array.add(10);
        array.remove(20);

        assertThat(array.length(), is(1));
        assertThat(array.get(0), is(10));
    }

    @Test
    public void shouldHandleRemoveOfNonExistentElementFromArray()
    {
        final AtomicArray<Integer> array = new AtomicArray<>();

        array.add(10);
        array.add(20);
        array.remove(30);

        assertThat(array.length(), is(2));
        assertThat(array.get(0), is(10));
        assertThat(array.get(1), is(20));
    }

    @Test
    public void shouldHandleRemoveElementFromArrayEnd()
    {
        final AtomicArray<Integer> array = new AtomicArray<>();

        array.add(10);
        array.add(20);
        array.remove(20);

        assertThat(array.length(), is(1));
        assertThat(array.get(0), is(10));
    }

    @Test
    public void shouldHandleRemoveElementFromArrayBegin()
    {
        final AtomicArray<Integer> array = new AtomicArray<>();

        array.add(10);
        array.add(20);
        array.remove(10);

        assertThat(array.length(), is(1));
        assertThat(array.get(0), is(20));
    }

    @Test
    public void shouldHandleRemoveElementFromArrayMiddle()
    {
        final AtomicArray<Integer> array = new AtomicArray<>();

        array.add(10);
        array.add(20);
        array.add(30);
        array.remove(20);

        assertThat(array.length(), is(2));
        assertThat(array.get(0), is(10));
        assertThat(array.get(1), is(30));
    }

    @Test
    public void forEachShouldIterateOverValuesInTheArray()
    {
        for (int start : new int[]{0, 1})
        {
            final Set<Integer> values = new HashSet<>(asList(10, 20, 30));
            final AtomicArray<Integer> array = new AtomicArray<>();
            values.forEach(array::add);

            array.forEach(start, values::remove);

            assertThat(values, empty());
        }
    }

    @Test
    public void shouldHandleStartTooLargeTransparently()
    {
        final Set<Integer> values = new HashSet<>(asList(10, 20, 30));
        final AtomicArray<Integer> array = new AtomicArray<>();
        values.forEach(array::add);

        array.forEach(4, values::remove);

        assertThat(values, empty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void arrayInitiallyUnchanged()
    {
        final AtomicArray<Integer> array = new AtomicArray<>();

        final Runnable init = mock(Runnable.class);
        final Consumer<Integer> handler = mock(Consumer.class);
        array.forEachIfChanged(init, handler);

        verifyNoMoreInteractions(init, handler);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void changesIdentified()
    {
        final AtomicArray<Integer> array = new AtomicArray<>();
        array.add(1);
        array.add(2);

        final Runnable init = mock(Runnable.class);
        final Consumer<Integer> handler = mock(Consumer.class);
        array.forEachIfChanged(init, handler);

        final InOrder inOrder = inOrder(init, handler);
        inOrder.verify(init).run();
        inOrder.verify(handler).accept(1);
        inOrder.verify(handler).accept(2);
        inOrder.verifyNoMoreInteractions();
    }
}
