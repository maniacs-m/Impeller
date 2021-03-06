package eu.e43.impeller.fragment;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.CursorLoader;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.LinearLayoutManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.json.JSONException;
import org.json.JSONObject;
import org.lucasr.twowayview.TwoWayLayoutManager;
import org.lucasr.twowayview.widget.TwoWayView;
import org.lucasr.twowayview.widget.ListLayoutManager;
import org.lucasr.twowayview.widget.StaggeredGridLayoutManager;

import java.net.URI;

import eu.e43.impeller.AppConstants;
import eu.e43.impeller.api.Constants;
import eu.e43.impeller.api.Content;
import eu.e43.impeller.ImpellerApplication;
import eu.e43.impeller.PostTask;
import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
import eu.e43.impeller.activity.ActivityWithAccount;
import eu.e43.impeller.activity.MainActivity;
import eu.e43.impeller.content.ContentUpdateReceiver;
import eu.e43.impeller.uikit.ActivityAdapter;
import eu.e43.impeller.uikit.AvatarView;
import eu.e43.impeller.uikit.ImageLoader;

/**
 * Created by oshepherd on 04/04/14.
 */
public class PersonObjectFragment extends ObjectFragment implements CompoundButton.OnCheckedChangeListener, LoaderManager.LoaderCallbacks<Cursor>, AdapterView.OnItemClickListener {
    private static final String TAG = "PersonObjectFragment";

    private MainActivity getMainActivity()
    {
        return (MainActivity) getActivity();
    }

    ActivityAdapter m_adapter;
    Palette         m_palette;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActivity().sendOrderedBroadcast(new Intent(
                ContentUpdateReceiver.FETCH_USER_FEED, Uri.parse(m_id),
                getActivity(), ContentUpdateReceiver.class
        ).putExtra(Constants.EXTRA_ACCOUNT, getMainActivity().getAccount()), null,
                null, null, Activity.RESULT_OK, null, null);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View header =  inflater.inflate(R.layout.view_person, null);

        ((TextView)header.findViewById(R.id.personLocationIcon))
                .setTypeface(ImpellerApplication.fontAwesome);



        TwoWayView lv = new TwoWayView(getActivity());
        lv.setId(android.R.id.list);
        lv.setPadding(0, 0, 0, 0);
        m_adapter = new ActivityAdapter(getMainActivity());
        getLoaderManager().initLoader(0, null, this);
        lv.setAdapter(m_adapter);

        lv.setLayoutManager(new ListLayoutManager(getActivity(), TwoWayLayoutManager.Orientation.VERTICAL));

        LinearLayout root = new LinearLayout(getActivity());
        root.addView(header);
        root.addView(lv);

        objectUpdated(getObject(), root);
        return root;
    }

    public void objectUpdated(JSONObject obj) {
        View root = getView();
        if(root == null) return;
        objectUpdated(obj, root);
    }

    private void objectUpdated(JSONObject obj, View root) {
        JSONObject pump_io = obj.optJSONObject("pump_io");
        if(pump_io == null) pump_io = new JSONObject();

        View       header             =                  root.findViewById(R.id.personHeader);
        AvatarView personAvatar       = (AvatarView)     root.findViewById(R.id.personImage);
        TextView   personName         = (TextView)       root.findViewById(R.id.personName);
        TextView   personDescription  = (TextView)       root.findViewById(R.id.personDescription);
        View       personLocationC    =                  root.findViewById(R.id.personLocationContainer);
        TextView   personLocation     = (TextView)       root.findViewById(R.id.personLocation);
        TextView   personLocationIcon = (TextView)       root.findViewById(R.id.personLocationIcon);
        ToggleButton personFollowed   = (ToggleButton)   root.findViewById(R.id.personFollowToggle);

        if(root != null && personAvatar == null) {
            Log.e(TAG, "root != null but avatar == null?");
        }

        JSONObject img = obj.optJSONObject("image");
        if(img != null) {
            ImageLoader ldr = getMainActivity().getImageLoader();
            ldr.setImage(personAvatar, Utils.getImageUrl(getMainActivity(), img), m_paletteListener);
        }

        personName.setText(obj.optString("displayName"));
        personDescription.setText(obj.optString("summary"));

        JSONObject loc = obj.optJSONObject("location");
        if(loc != null) {
            personLocationC.setVisibility(View.VISIBLE);
            personLocation.setText(loc.optString("displayName"));
        } else personLocationC.setVisibility(View.GONE);

        personFollowed.setChecked(pump_io.optBoolean("followed", false));
        personFollowed.setOnCheckedChangeListener(this);

        if(m_palette != null) {
            Palette.Swatch muted = m_palette.getVibrantSwatch();
            if(muted != null) {
                header.setBackgroundColor(muted.getRgb());
                personName.setTextColor(muted.getTitleTextColor());
                personDescription.setTextColor(muted.getBodyTextColor());
                personLocation.setTextColor(muted.getBodyTextColor());
                personLocationIcon.setTextColor(muted.getBodyTextColor());
            }
        }

    }

    private ImageLoader.Listener m_paletteListener = new ImageLoader.Listener() {
        @Override
        public void loaded(BitmapDrawable dr, URI uri) {
            Palette.generateAsync(dr.getBitmap(), new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(Palette palette) {
                    m_palette = palette;

                    objectUpdated(getObject());
                }
            });
        }

        @Override
        public void error(URI uri) {}
    };

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        // Only follow button for now - so we assume its' that. A good assumption, one would hope.
        new DoFollow(isChecked);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        JSONObject activity = (JSONObject) parent.getItemAtPosition(position);
        JSONObject object = activity.optJSONObject("object");
        if(object != null) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(object.optString("id")),
                    getActivity(), MainActivity.class));
        }
    }

    // ================
    // Follow Support
    // ================
    private class DoFollow implements PostTask.Callback {
        private JSONObject m_object;

        public DoFollow(boolean state) {
            String action;
            try {
                JSONObject obj = Utils.buildStubObject(getObject());

                if(state)
                    action = "follow";
                else
                    action = "stop-following";

                JSONObject act = new JSONObject();
                act.put("verb", action);
                act.put("object", obj);

                PostTask task = new PostTask((ActivityWithAccount) getActivity(), this);
                task.execute(act.toString());
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void call(JSONObject act) {
            if(act == null) return;

            try {
                JSONObject obj = act.getJSONObject("object");

                JSONObject pump_io = obj.optJSONObject("pump_io");
                if(pump_io == null) pump_io = new JSONObject();
                pump_io.put("followed", act.getString("verb").equals("follow"));
                obj.put("pump_io", pump_io);
                act.put("object", obj);

                ContentValues cv = new ContentValues();
                cv.put("_json", act.toString());

                MainActivity activity = getMainActivity();
                activity.getContentResolver().insert(activity.getContentUris().activitiesUri, cv);

                activity.getContentResolver().requestSync(
                        activity.getAccount(), Content.AUTHORITY, new Bundle());
            } catch (JSONException e) {
                Log.v(TAG, "Swallowing exception", e);
            }
        }
    }

    // ================
    // Loader callbacks
    // ================
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri uri = getMainActivity().getContentUris().activitiesUri;

        return new CursorLoader(getActivity(), uri,
           new String[] { "_ID", "object.id", "_json", "replies", "likes", "shares" },
           "activity.actor=?", new String[] { getObject().optString("id") },
           "activity.published DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if(loader != null && data != null) {
            data.setNotificationUri(
                    getActivity().getContentResolver(),
                    ((CursorLoader) loader).getUri());
        }
        m_adapter.updateCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        m_adapter.updateCursor(null);
    }
}
