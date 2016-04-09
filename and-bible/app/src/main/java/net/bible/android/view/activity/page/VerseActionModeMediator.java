package net.bible.android.view.activity.page;

import android.content.Intent;
import android.support.v7.view.ActionMode;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.event.touch.ShowContextMenuEvent;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.comparetranslations.CompareTranslations;
import net.bible.android.view.activity.footnoteandref.FootnoteAndRefActivity;

import org.crosswire.jsword.passage.Passage;
import org.crosswire.jsword.passage.Verse;

import de.greenrobot.event.EventBus;

/**
 * Control the verse selection action mode
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class VerseActionModeMediator {

	private MainBibleActivity mainBibleActivity;

	private Verse verse;

    boolean isVerseActionMode;

	private ActionMode actionMode;

	private CurrentPageManager currentPageManager;

    private static final String TAG = "VerseActionModeMediator";

	public VerseActionModeMediator() {
		ControlFactory.getInstance().inject(this);
	}

	public void setMainBibleActivity(MainBibleActivity mainBibleActivity) {
		this.mainBibleActivity = mainBibleActivity;
	}

	public void setCurrentPageManager(CurrentPageManager currentPageManager) {
		this.currentPageManager = currentPageManager;
	}

	public void verseLongPress(int verse) {
        Log.d(TAG, "Verse selected event:"+verse);
        startVerseActionMode(verse);
    }

    private void startVerseActionMode(int verse) {
		isVerseActionMode = true;
		this.verse = getSelectedVerse(verse);
		actionMode = mainBibleActivity.showVerseActionModeMenu(actionModeCallbackHandler);
    }

	/**
	 * Ensure all state is left tidy
	 */
	private void endVerseActionMode() {
		Log.d(TAG, "Ending action mode");
		isVerseActionMode = false;
		verse = null;
		// prevent endless loop by onDestroyActionMode calling this calling onDestroyActionMode etc.
		if (actionMode!=null) {
			ActionMode finishingActionMode = this.actionMode;
			actionMode = null;
			finishingActionMode.finish();
		}
	}

	private Verse getSelectedVerse(int verseNo) {
		Verse mainVerse = currentPageManager.getCurrentBible().getSingleKey();
		return new Verse(mainVerse.getVersification(), mainVerse.getBook(), mainVerse.getChapter(), verseNo);
	}

	private ActionMode.Callback actionModeCallbackHandler = new ActionMode.Callback() {
		@Override
		public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
			// Inflate our menu from a resource file
			actionMode.getMenuInflater().inflate(R.menu.document_viewer_context_menu, menu);

			// Return true so that the action mode is shown
			return true;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
			boolean isVerseBookmarked = ControlFactory.getInstance().getBookmarkControl().isBookmarkForKey(verse);
			menu.findItem(R.id.add_bookmark).setVisible(!isVerseBookmarked);
			menu.findItem(R.id.delete_bookmark).setVisible(isVerseBookmarked);

			// must return true if menu changed
			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
			// Similar to menu handling in Activity.onOptionsItemSelected()
			Intent handlerIntent = null;
			int requestCode = ActivityBase.STD_REQUEST_CODE;

			// Handle item selection
			switch (menuItem.getItemId()) {
				case R.id.compareTranslations:
					handlerIntent = new Intent(mainBibleActivity, CompareTranslations.class);
					break;
				case R.id.notes:
					handlerIntent = new Intent(mainBibleActivity, FootnoteAndRefActivity.class);
					break;
				case R.id.add_bookmark:
				case R.id.delete_bookmark:
					ControlFactory.getInstance().getBookmarkControl().toggleBookmarkForVerse(verse);
					// refresh view to show new bookmark icon
					PassageChangeMediator.getInstance().forcePageUpdate();
					break;
				case R.id.myNoteAddEdit:
					ControlFactory.getInstance().getCurrentPageControl().showMyNote();
					break;
				case R.id.copy:
					ControlFactory.getInstance().getPageControl().copyToClipboard();
					break;
				case R.id.shareVerse:
					ControlFactory.getInstance().getPageControl().shareVerse();
					break;
			}

			if (handlerIntent!=null) {
				handlerIntent.putExtra(CompareTranslations.VERSE, verse.getOsisID());
				mainBibleActivity.startActivityForResult(handlerIntent, requestCode);
			}

			endVerseActionMode();

			// handle all
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode actionMode) {
			endVerseActionMode();
		}
	};

	public interface ActionModeMenuDisplay {
		ActionMode showVerseActionModeMenu(ActionMode.Callback actionModeCallbackHandler);
	}

}
