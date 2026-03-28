/**
 * Likely Destinations Management Screen
 * Places the person may wander to during an emergency
 */

import { useCallback, useEffect, useState } from 'react';
import { useNavigation } from '@react-navigation/native';
import {
  Alert,
  Linking,
  Platform,
  Pressable,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import DraggableFlatList, { RenderItemParams } from '@/utils/draggable-flatlist';

import { AppModal } from '@/components/AppModal';
import { AppTextInput } from '@/components/AppTextInput';
import { ScreenHeader } from '@/components/ScreenHeader';
import { ThemedText } from '@/components/ThemedText';
import { ThemedView } from '@/components/ThemedView';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors, primary, semantic } from '@/constants/Colors';
// neutral is intentionally not imported — all neutral refs use theme tokens
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { useTheme } from '@/context/ThemeContext';
import {
  createDestination,
  deleteDestination,
  Destination,
  getDestinations,
  updateDestination,
} from '@/database/destinations';

type DestinationCategory =
  | 'water'
  | 'former_workplace'
  | 'church'
  | 'store'
  | 'restaurant'
  | 'friend_family'
  | 'walking_route'
  | 'other';
type RiskLevel = 'high' | 'medium' | 'low';

const CATEGORY_OPTIONS: { value: DestinationCategory; label: string; icon: string }[] = [
  { value: 'water', label: 'Water/Pool', icon: 'drop.fill' },
  { value: 'former_workplace', label: 'Former Work', icon: 'briefcase.fill' },
  { value: 'church', label: 'Church', icon: 'building.columns.fill' },
  { value: 'store', label: 'Store', icon: 'cart.fill' },
  { value: 'restaurant', label: 'Restaurant', icon: 'fork.knife' },
  { value: 'friend_family', label: 'Friend/Family', icon: 'person.2.fill' },
  { value: 'walking_route', label: 'Walking Route', icon: 'figure.walk' },
  { value: 'other', label: 'Other', icon: 'mappin' },
];

const RISK_OPTIONS: { value: RiskLevel; label: string; color: keyof typeof semantic }[] = [
  { value: 'high', label: 'High', color: 'error' },
  { value: 'medium', label: 'Medium', color: 'warning' },
  { value: 'low', label: 'Low', color: 'success' },
];

interface FormData {
  name: string;
  address: string;
  category: DestinationCategory;
  riskLevel: RiskLevel;
  reason: string;
  distanceFromHome: string;
  notes: string;
}

const EMPTY_FORM: FormData = {
  name: '',
  address: '',
  category: 'other',
  riskLevel: 'medium',
  reason: '',
  distanceFromHome: '',
  notes: '',
};

export default function DestinationsScreen() {
  const navigation = useNavigation();
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];

  const [destinations, setDestinations] = useState<Destination[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [editingDestination, setEditingDestination] = useState<Destination | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<FormData>(EMPTY_FORM);
  const [initialFormData, setInitialFormData] = useState<FormData>(EMPTY_FORM);
  const [isSaving, setIsSaving] = useState(false);

  const hasUnsavedFormChanges =
    showForm && JSON.stringify(formData) !== JSON.stringify(initialFormData);

  // Modal state
  const [modalVisible, setModalVisible] = useState(false);
  const [modalType, setModalType] = useState<'delete' | 'error' | 'validation' | null>(null);
  const [modalMessage, setModalMessage] = useState('');
  const [pendingDelete, setPendingDelete] = useState<Destination | null>(null);

  const loadDestinations = useCallback(async () => {
    try {
      const data = await getDestinations();
      setDestinations(data);
    } catch (error) {
      console.error('Failed to load destinations:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDestinations();
  }, [loadDestinations]);

  const discardFormAndClose = () => {
    setShowForm(false);
    setEditingDestination(null);
    setFormData(EMPTY_FORM);
    setInitialFormData(EMPTY_FORM);
  };

  const showAlert = (type: 'error' | 'validation', message: string) => {
    if (Platform.OS === 'web') {
      setModalType(type);
      setModalMessage(message);
      setModalVisible(true);
    } else {
      Alert.alert(type === 'validation' ? 'Required' : 'Error', message);
    }
  };

  const handleSave = async () => {
    if (!formData.name.trim()) {
      showAlert('validation', 'Please enter a name for this location.');
      return;
    }

    setIsSaving(true);
    try {
      if (editingDestination?.id) {
        await updateDestination(editingDestination.id, {
          name: formData.name,
          address: formData.address || undefined,
          category: formData.category,
          riskLevel: formData.riskLevel,
          reason: formData.reason || undefined,
          distanceFromHome: formData.distanceFromHome || undefined,
          notes: formData.notes || undefined,
        });
      } else {
        const nextSortOrder = destinations.reduce((maxOrder, destination) => {
          return Math.max(maxOrder, destination.sortOrder ?? 0);
        }, -1);

        await createDestination({
          name: formData.name,
          address: formData.address || undefined,
          category: formData.category,
          riskLevel: formData.riskLevel,
          reason: formData.reason || undefined,
          distanceFromHome: formData.distanceFromHome || undefined,
          notes: formData.notes || undefined,
          sortOrder: nextSortOrder + 1,
        });
      }

      await loadDestinations();
      discardFormAndClose();
    } catch (error) {
      console.error('Failed to save destination:', error);
      showAlert('error', 'Failed to save location. Please try again.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleEdit = (destination: Destination) => {
    setEditingDestination(destination);
    const nextFormData = {
      name: destination.name,
      address: destination.address || '',
      category: destination.category || 'other',
      riskLevel: destination.riskLevel || 'medium',
      reason: destination.reason || '',
      distanceFromHome: destination.distanceFromHome || '',
      notes: destination.notes || '',
    };
    setFormData(nextFormData);
    setInitialFormData(nextFormData);
    setShowForm(true);
  };

  const handleDelete = (destination: Destination) => {
    if (Platform.OS === 'web') {
      setPendingDelete(destination);
      setModalType('delete');
      setModalMessage(`Are you sure you want to remove "${destination.name}"?`);
      setModalVisible(true);
    } else {
      Alert.alert('Delete Location', `Are you sure you want to remove "${destination.name}"?`, [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () => confirmDelete(destination),
        },
      ]);
    }
  };

  const confirmDelete = async (destination: Destination) => {
    try {
      if (destination.id) {
        await deleteDestination(destination.id);
        await loadDestinations();
        if (editingDestination?.id === destination.id) {
          discardFormAndClose();
        }
      }
    } catch (error) {
      console.error('Failed to delete destination:', error);
      showAlert('error', 'Failed to delete location.');
    }
  };

  const handleModalAction = (action: 'confirm' | 'cancel') => {
    setModalVisible(false);
    if (action === 'confirm' && modalType === 'delete' && pendingDelete) {
      confirmDelete(pendingDelete);
    }
    setPendingDelete(null);
    setModalType(null);
    setModalMessage('');
  };

  const handleOpenMaps = (address: string) => {
    const encoded = encodeURIComponent(address);
    const url = Platform.select({
      ios: `maps:?q=${encoded}`,
      android: `geo:0,0?q=${encoded}`,
      default: `https://www.google.com/maps/search/?api=1&query=${encoded}`,
    });
    if (url) Linking.openURL(url);
  };

  const handleAddNew = () => {
    setEditingDestination(null);
    setFormData(EMPTY_FORM);
    setInitialFormData(EMPTY_FORM);
    setShowForm(true);
  };

  const handleCancel = () => {
    if (!hasUnsavedFormChanges) {
      discardFormAndClose();
      return;
    }

    Alert.alert('Discard Changes?', 'You have unsaved changes to this location.', [
      { text: 'Keep Editing', style: 'cancel' },
      {
        text: 'Discard',
        style: 'destructive',
        onPress: discardFormAndClose,
      },
    ]);
  };

  useEffect(() => {
    const unsubscribe = navigation.addListener('beforeRemove', (event) => {
      if (!hasUnsavedFormChanges || isSaving) {
        return;
      }

      event.preventDefault();

      Alert.alert('Discard Changes?', 'You have unsaved changes to this location.', [
        { text: 'Keep Editing', style: 'cancel' },
        {
          text: 'Discard',
          style: 'destructive',
          onPress: () => {
            discardFormAndClose();
            navigation.dispatch(event.data.action);
          },
        },
      ]);
    });

    return unsubscribe;
  }, [navigation, hasUnsavedFormChanges, isSaving]);

  const getCategoryInfo = (category?: DestinationCategory) => {
    return CATEGORY_OPTIONS.find((c) => c.value === category) || CATEGORY_OPTIONS[7];
  };

  const getRiskInfo = (riskLevel?: RiskLevel) => {
    return RISK_OPTIONS.find((r) => r.value === riskLevel) || RISK_OPTIONS[1];
  };

  const handleDragEnd = async ({ data }: { data: Destination[] }) => {
    const updates = data
      .map((destination, index) => ({
        id: destination.id,
        previousSortOrder: destination.sortOrder,
        newSortOrder: index,
      }))
      .filter((destination) => destination.id && destination.previousSortOrder !== destination.newSortOrder);

    const reordered = data.map((destination, index) => ({
      ...destination,
      sortOrder: index,
    }));
    setDestinations(reordered);

    if (updates.length === 0) {
      return;
    }

    try {
      await Promise.all(
        updates.map((destination) =>
          updateDestination(destination.id!, { sortOrder: destination.newSortOrder }),
        ),
      );
      await loadDestinations();
    } catch (error) {
      console.error('Failed to reorder destinations:', error);
      showAlert('error', 'Failed to save location order. Please try again.');
      await loadDestinations();
    }
  };

  const renderDestinationCard = ({ item: destination, drag, isActive }: RenderItemParams<Destination>) => {
    const categoryInfo = getCategoryInfo(destination.category);
    const riskInfo = getRiskInfo(destination.riskLevel);
    const riskColor = semantic[riskInfo.color];
    const isWater = destination.category === 'water';

    return (
      <Pressable
        onLongPress={drag}
        delayLongPress={180}
        style={[
          styles.card,
          isActive && styles.cardActive,
          {
            backgroundColor: theme.card,
            borderColor: theme.border,
            borderLeftColor: riskColor,
          },
        ]}
      >
        <View style={styles.cardHeader}>
          <View style={styles.cardInfo}>
            <View style={styles.nameRow}>
              <View
                style={[
                  styles.categoryIcon,
                  { backgroundColor: isWater ? `${semantic.warning}20` : `${primary[600]}15` },
                ]}
              >
                <IconSymbol
                  name={categoryInfo.icon as any}
                  size={16}
                  color={isWater ? semantic.warning : primary[600]}
                />
              </View>
              <ThemedText style={styles.destinationName} numberOfLines={1}>
                {destination.name}
              </ThemedText>
            </View>
            <View style={styles.metaRow}>
              <View style={[styles.riskBadge, { backgroundColor: `${riskColor}20` }]}>
                <ThemedText style={[styles.riskText, { color: riskColor }]}>
                  {riskInfo.label}
                </ThemedText>
              </View>
              <ThemedText style={[styles.categoryText, { color: theme.textSecondary }]}>
                {categoryInfo.label}
              </ThemedText>
            </View>
          </View>

          <TouchableOpacity
            style={[styles.editButton, { backgroundColor: `${primary[600]}15` }]}
            onPress={() => handleEdit(destination)}
          >
            <IconSymbol name="pencil" size={14} color={primary[600]} />
          </TouchableOpacity>
        </View>

        {destination.address && (
          <TouchableOpacity
            style={[styles.addressRow, { backgroundColor: theme.surface }]}
            onPress={() => handleOpenMaps(destination.address!)}
          >
            <IconSymbol name="location" size={14} color={primary[600]} />
            <ThemedText
              style={styles.addressText}
              lightColor={primary[700]}
              darkColor={primary[300]}
              numberOfLines={2}
            >
              {destination.address}
            </ThemedText>
            <IconSymbol name="arrow.up.right" size={12} color={primary[600]} />
          </TouchableOpacity>
        )}

        {destination.distanceFromHome && (
          <View style={styles.detailRow}>
            <IconSymbol name="arrow.left.arrow.right" size={12} color={theme.icon} />
            <ThemedText style={[styles.detailText, { color: theme.textSecondary }]}>
              {destination.distanceFromHome} from home
            </ThemedText>
          </View>
        )}

        {destination.reason && (
          <View style={styles.reasonBox}>
            <ThemedText style={[styles.reasonLabel, { color: theme.textSecondary }]}>
              Why they might go here:
            </ThemedText>
            <ThemedText style={[styles.reasonText, { color: theme.text }]}>
              {destination.reason}
            </ThemedText>
          </View>
        )}
      </Pressable>
    );
  };

  const renderForm = () => (
    <ScrollView style={styles.formContainer} showsVerticalScrollIndicator={false}>
      <ThemedText style={styles.formTitle}>
        {editingDestination ? 'Edit Location' : 'Add Likely Destination'}
      </ThemedText>

      <ThemedText style={[styles.formHint, { color: theme.textSecondary }]}>
        Add places your loved one may wander to. These will be checked during a search.
      </ThemedText>

      {/* Name */}
      <AppTextInput
        label="Location Name"
        required
        placeholder="e.g., Riverside Park, Old House on Maple St"
        value={formData.name}
        onChangeText={(text) => setFormData({ ...formData, name: text })}
      />

      {/* Category */}
      <View style={styles.formField}>
        <ThemedText style={[styles.fieldLabel, { color: theme.text }]}>Location Type</ThemedText>
        <View style={styles.optionGrid}>
          {CATEGORY_OPTIONS.map((option) => {
            const isWater = option.value === 'water';
            const isSelected = formData.category === option.value;
            return (
              <TouchableOpacity
                key={option.value}
                style={[
                  styles.optionButton,
                  {
                    backgroundColor: isSelected
                      ? isWater
                        ? semantic.warning
                        : primary[600]
                      : theme.inputBackground,
                    borderColor: isSelected
                      ? isWater
                        ? semantic.warning
                        : primary[600]
                      : theme.inputBorder,
                  },
                ]}
                onPress={() => setFormData({ ...formData, category: option.value })}
              >
                <IconSymbol
                  name={option.icon as any}
                  size={14}
                  color={isSelected ? '#fff' : theme.icon}
                />
                <ThemedText
                  style={[
                    styles.optionText,
                    isSelected ? { color: '#fff' } : { color: theme.text },
                  ]}
                >
                  {option.label}
                </ThemedText>
              </TouchableOpacity>
            );
          })}
        </View>
        {formData.category === 'water' && (
          <View style={[styles.warningBox, { backgroundColor: `${semantic.warning}15` }]}>
            <IconSymbol name="exclamationmark.triangle.fill" size={16} color={semantic.warning} />
            <ThemedText style={[styles.warningText, { color: semantic.warning }]}>
              Water locations are high priority. Always check water first!
            </ThemedText>
          </View>
        )}
      </View>

      {/* Risk Level */}
      <View style={styles.formField}>
        <ThemedText style={[styles.fieldLabel, { color: theme.text }]}>Search Priority</ThemedText>
        <View style={styles.riskRow}>
          {RISK_OPTIONS.map((option) => {
            const color = semantic[option.color];
            const isSelected = formData.riskLevel === option.value;
            return (
              <TouchableOpacity
                key={option.value}
                style={[
                  styles.riskOption,
                  {
                    backgroundColor: isSelected ? color : 'transparent',
                    borderColor: color,
                  },
                ]}
                onPress={() => setFormData({ ...formData, riskLevel: option.value })}
              >
                <ThemedText style={[styles.riskOptionText, { color: isSelected ? '#fff' : color }]}>
                  {option.label}
                </ThemedText>
              </TouchableOpacity>
            );
          })}
        </View>
      </View>

      {/* Address */}
      <AppTextInput
        label="Address"
        placeholder="Street address or description"
        multiline
        value={formData.address}
        onChangeText={(text) => setFormData({ ...formData, address: text })}
      />

      {/* Distance */}
      <AppTextInput
        label="Distance from Home"
        placeholder="e.g., 0.5 miles, 2 blocks"
        value={formData.distanceFromHome}
        onChangeText={(text) => setFormData({ ...formData, distanceFromHome: text })}
      />

      {/* Reason */}
      <AppTextInput
        label="Why They Might Go Here"
        placeholder="e.g., Lived here 30 years ago, Used to work here"
        multiline
        value={formData.reason}
        onChangeText={(text) => setFormData({ ...formData, reason: text })}
      />

      {/* Notes */}
      <AppTextInput
        label="Additional Notes"
        placeholder="Any other helpful details..."
        multiline
        value={formData.notes}
        onChangeText={(text) => setFormData({ ...formData, notes: text })}
      />

      {editingDestination && (
        <TouchableOpacity
          style={[styles.formDeleteButton, { borderColor: semantic.error }]}
          onPress={() => handleDelete(editingDestination)}
        >
          <IconSymbol name="trash" size={14} color={semantic.error} />
          <ThemedText style={[styles.formDeleteText, { color: semantic.error }]}>
            Delete Location
          </ThemedText>
        </TouchableOpacity>
      )}

      <View style={{ height: 40 }} />
    </ScrollView>
  );

  const renderDestinationList = () => (
    <DraggableFlatList
      data={destinations}
      keyExtractor={(destination: Destination) => String(destination.id ?? destination.name)}
      onDragEnd={handleDragEnd}
      activationDistance={8}
      autoscrollThreshold={120}
      containerStyle={styles.listContainer}
      contentContainerStyle={[styles.listContent, destinations.length === 0 && styles.emptyListContainer]}
      showsVerticalScrollIndicator={false}
      renderItem={renderDestinationCard}
      ListHeaderComponent={
        <>
          <View style={[styles.infoBox, { backgroundColor: theme.primaryLight }]}> 
            <IconSymbol name="info.circle.fill" size={18} color={primary[600]} />
            <ThemedText style={[styles.infoText, { color: theme.text }]}> 
              Most people with dementia are found within 1.5 miles of where they were last seen. Check
              water sources first!
            </ThemedText>
          </View>

          {destinations.length > 0 && (
            <View style={styles.listHeader}>
              <ThemedText style={[styles.listCount, { color: theme.textSecondary }]}> 
                {destinations.length} location{destinations.length !== 1 ? 's' : ''}
              </ThemedText>
              <ThemedText style={[styles.listHint, { color: theme.textSecondary }]}> 
                Press and hold any location card to reorder search priority.
              </ThemedText>
            </View>
          )}
        </>
      }
      ListEmptyComponent={
        <View style={styles.emptyState}>
          <View style={[styles.emptyIconWrap, { backgroundColor: theme.primaryLight }]}> 
            <IconSymbol name="mappin.and.ellipse" size={40} color={theme.primary} />
          </View>
          <ThemedText type="title" style={[styles.emptyTitle, { color: theme.text }]}> 
            No Destinations Added
          </ThemedText>
          <ThemedText style={[styles.emptyText, { color: theme.textSecondary }]}> 
            Add places your loved one may wander to, like former homes, favorite stores, or water
            sources.
          </ThemedText>
          <Pressable
            style={[styles.emptyButton, { backgroundColor: theme.primary }]}
            onPress={handleAddNew}
          >
            <IconSymbol name="plus" size={18} color="#fff" />
            <ThemedText style={[styles.emptyButtonText, { color: theme.textOnPrimary }]}> 
              Add First Location
            </ThemedText>
          </Pressable>
        </View>
      }
      ListFooterComponent={
        <>
          {destinations.length > 0 && (
            <TouchableOpacity
              style={[styles.addButton, { backgroundColor: theme.primary }]}
              onPress={handleAddNew}
            >
              <IconSymbol name="plus" size={20} color="#fff" />
              <ThemedText style={styles.addButtonText}>Add Location</ThemedText>
            </TouchableOpacity>
          )}
          <View style={{ height: 40 }} />
        </>
      }
    />
  );

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea} edges={['top']}>
        {/* Header */}
        <ScreenHeader
          title={
            showForm
              ? editingDestination
                ? 'Edit Location'
                : 'Add Location'
              : 'Likely Destinations'
          }
          onBack={showForm ? handleCancel : undefined}
          rightElement={
            showForm ? (
              <Pressable
                onPress={handleSave}
                style={[styles.headerSaveButton, { backgroundColor: theme.tint }]}
                disabled={isSaving}
              >
                <ThemedText style={styles.headerSaveText} numberOfLines={1}>
                  {isSaving ? 'Saving...' : editingDestination ? 'Update' : 'Add'}
                </ThemedText>
              </Pressable>
            ) : undefined
          }
        />

        {/* Content */}
        {isLoading ? (
          <View style={styles.loading}>
            <ThemedText>Loading...</ThemedText>
          </View>
        ) : showForm ? (
          renderForm()
        ) : (
          renderDestinationList()
        )}
      </SafeAreaView>

      <AppModal
        visible={modalVisible}
        onDismiss={() => handleModalAction('cancel')}
        title={
          modalType === 'delete'
            ? 'Delete Location'
            : modalType === 'validation'
              ? 'Required'
              : 'Error'
        }
        message={modalMessage}
        type={modalType === 'delete' ? 'delete' : 'alert'}
        onConfirm={() => handleModalAction('confirm')}
      />
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
  loading: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerSaveButton: {
    minWidth: 72,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.xs,
    borderRadius: Radius.md,
    alignItems: 'center',
  },
  headerSaveText: {
    color: '#fff',
    ...Typography.bodyBold,
  },

  // List styles
  listContainer: {
    flex: 1,
  },
  listContent: {
    padding: Spacing.lg,
    paddingBottom: 0,
    flexGrow: 1,
  },
  emptyListContainer: {
    justifyContent: 'flex-start',
  },
  infoBox: {
    flexDirection: 'row',
    padding: Spacing.md,
    borderRadius: Radius.lg,
    gap: Spacing.md,
    marginBottom: Spacing.lg,
  },
  infoText: {
    flex: 1,
    ...Typography.caption,
  },
  listHeader: {
    marginBottom: Spacing.md,
  },
  listCount: {
    ...Typography.body,
  },
  listHint: {
    ...Typography.caption,
    marginTop: Spacing.xxs,
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: 40,
    paddingHorizontal: Spacing.xxl,
    gap: Spacing.md,
  },
  emptyIconWrap: {
    width: 88,
    height: 88,
    borderRadius: 44,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: Spacing.sm,
  },
  emptyTitle: {
    textAlign: 'center',
  },
  emptyText: {
    ...Typography.body,
    textAlign: 'center',
  },
  emptyButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    paddingHorizontal: Spacing.xl,
    paddingVertical: Spacing.md,
    borderRadius: Radius.lg,
    marginTop: Spacing.sm,
    minHeight: 48,
  },
  emptyButtonText: {
    ...Typography.bodyBold,
  },

  // Destination card
  card: {
    borderRadius: Radius.lg,
    borderWidth: 1,
    borderLeftWidth: 4,
    padding: Spacing.lg,
    marginBottom: Spacing.md,
  },
  cardActive: {
    opacity: 0.92,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 10,
  },
  cardInfo: {
    flex: 1,
  },
  nameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  categoryIcon: {
    width: 32,
    height: 32,
    borderRadius: Radius.md,
    justifyContent: 'center',
    alignItems: 'center',
  },
  destinationName: {
    ...Typography.bodyLarge,
    fontWeight: '600',
    flex: 1,
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    marginTop: 6,
    marginLeft: 42,
  },
  riskBadge: {
    paddingHorizontal: Spacing.sm,
    paddingVertical: Spacing.xxs,
    borderRadius: Radius.sm,
  },
  riskText: {
    ...Typography.small,
    fontWeight: '600',
  },
  categoryText: {
    ...Typography.caption,
  },
  editButton: {
    width: 32,
    height: 32,
    borderRadius: Radius.md,
    justifyContent: 'center',
    alignItems: 'center',
  },
  addressRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingVertical: Spacing.sm,
    paddingHorizontal: 10,
    borderRadius: Radius.md,
    marginBottom: Spacing.sm,
  },
  addressText: {
    ...Typography.caption,
    flex: 1,
  },
  detailRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginBottom: 6,
  },
  detailText: {
    ...Typography.caption,
  },
  reasonBox: {
    marginTop: Spacing.sm,
    paddingTop: Spacing.sm,
    borderTopWidth: 1,
    borderTopColor: 'rgba(128,128,128,0.2)',
  },
  reasonLabel: {
    ...Typography.small,
    fontWeight: '500',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: Spacing.xxs,
  },
  reasonText: {
    ...Typography.body,
    fontStyle: 'italic',
  },
  formDeleteButton: {
    marginTop: Spacing.md,
    borderWidth: 1,
    borderRadius: Radius.md,
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.xs,
    paddingHorizontal: Spacing.md,
  },
  formDeleteText: {
    ...Typography.bodyBold,
  },
  addButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.sm,
    padding: Spacing.lg,
    borderRadius: Radius.lg,
    marginTop: Spacing.sm,
  },
  addButtonText: {
    color: '#fff',
    ...Typography.bodyBold,
  },

  // Form styles
  formContainer: {
    flex: 1,
    padding: Spacing.lg,
  },
  formTitle: {
    ...Typography.headline,
    marginBottom: Spacing.sm,
  },
  formHint: {
    ...Typography.body,
    marginBottom: Spacing.xl,
  },
  formField: {
    marginBottom: Spacing.lg,
  },
  fieldLabel: {
    ...Typography.bodyBold,
    marginBottom: Spacing.xs,
  },
  optionGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  optionButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    borderRadius: Radius.md,
    borderWidth: 1,
  },
  optionText: {
    ...Typography.caption,
    fontWeight: '500',
  },
  warningBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    padding: 10,
    borderRadius: Radius.md,
    marginTop: Spacing.sm,
  },
  warningText: {
    ...Typography.caption,
    flex: 1,
  },
  riskRow: {
    flexDirection: 'row',
    gap: Spacing.md,
  },
  riskOption: {
    flex: 1,
    paddingVertical: Spacing.md,
    borderRadius: Radius.md,
    borderWidth: 2,
    alignItems: 'center',
  },
  riskOptionText: {
    ...Typography.body,
    fontWeight: '600',
  },
});
