package com.sister.habits.child;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ChildPagerAdapter extends FragmentStateAdapter {

    public ChildPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new ShopFragment();
            case 1: return new TaskFragment();
            case 2: return new WordFragment();
            default: return new ShopFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}